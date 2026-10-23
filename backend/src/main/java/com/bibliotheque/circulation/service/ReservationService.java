package com.bibliotheque.circulation.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.service.CopyCirculationService;
import com.bibliotheque.catalog.service.CopyCirculationService.BookAvailability;
import com.bibliotheque.catalog.service.CopyCirculationService.CopySnapshot;
import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.Reservation;
import com.bibliotheque.circulation.domain.ReservationStatus;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.circulation.repository.ReservationRepository;
import com.bibliotheque.circulation.service.CirculationViews.CopyRelease;
import com.bibliotheque.circulation.service.CirculationViews.CurrentReservation;
import com.bibliotheque.circulation.service.CirculationViews.Destination;
import com.bibliotheque.circulation.service.CirculationViews.HoldView;
import com.bibliotheque.circulation.service.CirculationViews.MemberBrief;
import com.bibliotheque.circulation.service.CirculationViews.MemberContactBrief;
import com.bibliotheque.circulation.service.CirculationViews.MemberReservation;
import com.bibliotheque.circulation.service.CirculationViews.MyBookReservation;
import com.bibliotheque.circulation.service.CirculationViews.PreviewMember;
import com.bibliotheque.circulation.service.CirculationViews.QueueEntry;
import com.bibliotheque.circulation.service.CirculationViews.ReadyReservation;
import com.bibliotheque.circulation.service.CirculationViews.ReleasedCopy;
import com.bibliotheque.circulation.service.CirculationViews.ReservationCancelled;
import com.bibliotheque.circulation.service.CirculationViews.ReservationCreated;
import com.bibliotheque.circulation.service.CirculationViews.ReservationPreview;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.settings.SettingCode;
import com.bibliotheque.common.settings.SettingsService;
import com.bibliotheque.member.service.MemberCirculation.Eligibility;
import com.bibliotheque.member.service.MemberLookup;
import com.bibliotheque.member.service.MemberViews.MemberRef;

/** Réservations (US-032 → US-035, RG-29 → RG-34). */
@Service
public class ReservationService {

    public static final String COPY_AVAILABLE_ON_SHELF = "COPY_AVAILABLE_ON_SHELF";
    public static final String NO_COPY_IN_CIRCULATION = "NO_COPY_IN_CIRCULATION";
    public static final String RESERVATION_ALREADY_EXISTS = "RESERVATION_ALREADY_EXISTS";
    public static final String BOOK_ALREADY_BORROWED = "BOOK_ALREADY_BORROWED";
    public static final String RESERVATION_LIMIT_REACHED = "RESERVATION_LIMIT_REACHED";
    public static final String MEMBER_NOT_ELIGIBLE = "MEMBER_NOT_ELIGIBLE";

    static final int BATCH_SIZE = 500;

    private static final EnumSet<ReservationStatus> RELEASED =
            EnumSet.of(ReservationStatus.EXPIRED, ReservationStatus.CANCELLED);

    private final ReservationRepository reservations;
    private final LoanRepository loans;
    private final CopyCirculationService copies;
    private final MemberLookup members;
    private final EligibilityService eligibility;
    private final ReservationAllocator allocator;
    private final SettingsService settings;
    private final CirculationAudit audit;
    private final BusinessTime time;

    public ReservationService(ReservationRepository reservations, LoanRepository loans,
                              CopyCirculationService copies, MemberLookup members, EligibilityService eligibility,
                              ReservationAllocator allocator, SettingsService settings, CirculationAudit audit,
                              BusinessTime time) {
        this.reservations = reservations;
        this.loans = loans;
        this.copies = copies;
        this.members = members;
        this.eligibility = eligibility;
        this.allocator = allocator;
        this.settings = settings;
        this.audit = audit;
        this.time = time;
    }

    /** ECR-30, ECR-31 : réservabilité, motifs et position prévisionnelle. */
    @Transactional(readOnly = true)
    public ReservationPreview preview(long bookId, MemberRef member, boolean includeMember) {
        copies.titleOf(bookId);
        Check check = check(bookId, member);
        PreviewMember previewMember = includeMember
                ? new PreviewMember(member.memberNumber(), member.lastName(), member.firstName(), member.status())
                : null;
        return new ReservationPreview(check.reasons().isEmpty(), check.reasons(), check.memberReasons(),
                reservations.countByBookIdAndStatus(bookId, ReservationStatus.PENDING) + 1, check.activeReservations(),
                check.limit(), settings.getInt(SettingCode.RESERVATION_PICKUP_DAYS), previewMember);
    }

    /** ECR-09 : réservation active de l'adhérent connecté sur cette notice, et réservabilité. */
    @Transactional(readOnly = true)
    public MyBookReservation myBookReservation(long bookId, long memberId) {
        MemberRef member = members.require(memberId);
        ReservationPreview preview = preview(bookId, member, false);
        CurrentReservation current = reservations
                .findFirstByMemberIdAndBookIdAndStatusIn(memberId, bookId, Reservation.ACTIVE)
                .map(this::current).orElse(null);
        return new MyBookReservation(current, preview);
    }

    /** US-032 : par l'adhérent (ECR-30) ou par un agent pour lui (AC-032-8). */
    @Transactional
    public ReservationCreated create(long bookId, String memberNumber, long actorAccountId) {
        return create(bookId, members.lock(memberNumber), actorAccountId);
    }

    @Transactional
    public ReservationCreated create(long bookId, long memberId, long actorAccountId) {
        return create(bookId, members.lock(memberId), actorAccountId);
    }

    private ReservationCreated create(long bookId, MemberRef member, long actorAccountId) {
        String title = copies.titleOf(bookId);
        Check check = check(bookId, member);
        if (!check.reasons().isEmpty()) {
            throw refusal(check);
        }
        Reservation reservation = reservations.save(new Reservation(bookId, member.id(), time.now(), actorAccountId));
        reservations.flush();
        long position = reservations.positionOf(bookId, reservation.getReservedAt(), reservation.getId());
        audit.log(actorAccountId, AuditAction.CREATE, AuditObjectType.RESERVATION, reservation.getId(), Map.of(
                "bookId", bookId, "title", title, "memberNumber", member.memberNumber(), "position", position));
        return new ReservationCreated(reservation.getId(), reservation.getStatus(), position,
                reservations.countByBookIdAndStatus(bookId, ReservationStatus.PENDING));
    }

    /**
     * US-033, RG-34 : annulation par l'adhérent ({@code ownerMemberId} renseigné) ou par le personnel ; l'exemplaire
     * d'une réservation prête est réattribué (RG-32).
     */
    @Transactional
    public ReservationCancelled cancel(long reservationId, Long ownerMemberId, long actorAccountId) {
        Reservation reservation = reservations.findById(reservationId)
                .filter(found -> ownerMemberId == null || ownerMemberId.equals(found.getMemberId()))
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESERVATION_NOT_FOUND, "Réservation introuvable."));
        boolean wasReady = reservation.getStatus() == ReservationStatus.READY;
        CopySnapshot copy = wasReady ? copies.lockById(reservation.getCopyId()) : null;
        reservation.cancel(time.now(), actorAccountId);
        reservations.flush();
        audit.log(actorAccountId, AuditAction.STATE_CHANGE, AuditObjectType.RESERVATION, reservationId,
                Map.of("from", wasReady ? "READY" : "PENDING", "to", "CANCELLED"));
        CopyRelease release = copy == null ? null : allocator.allocate(copy, actorAccountId);
        return new ReservationCancelled(reservationId, reservation.getStatus(), release);
    }

    /** ECR-18, ECR-22 : réservations actives d'un adhérent avec leur position (jamais l'identité des autres). */
    @Transactional(readOnly = true)
    public List<MemberReservation> memberReservations(long memberId) {
        List<Reservation> active = reservations.findByMemberIdAndStatusInOrderByReservedAtAscIdAsc(memberId,
                Reservation.ACTIVE);
        Map<Long, String> titles = copies.titlesOf(active.stream().map(Reservation::getBookId).distinct().toList());
        Map<Long, CopySnapshot> held = copies.describe(active.stream().map(Reservation::getCopyId)
                .filter(Objects::nonNull).toList());
        return active.stream().map(reservation -> {
            CurrentReservation current = current(reservation);
            CopySnapshot copy = reservation.getCopyId() == null ? null : held.get(reservation.getCopyId());
            return new MemberReservation(reservation.getId(), reservation.getBookId(),
                    titles.get(reservation.getBookId()), reservation.getStatus(), current.position(),
                    current.queueLength(), reservation.getReservedAt(), reservation.getPickupDeadline(),
                    copy == null ? null : copy.copyNumber());
        }).toList();
    }

    /** US-034 : la réservation prête d'abord, puis la file par position. */
    @Transactional(readOnly = true)
    public List<QueueEntry> queue(long bookId) {
        copies.titleOf(bookId);
        List<Reservation> queue = reservations.findQueue(bookId, Reservation.ACTIVE);
        Map<Long, MemberRef> membersById = members.refs(queue.stream().map(Reservation::getMemberId).toList());
        Map<Long, CopySnapshot> held = copies.describe(queue.stream().map(Reservation::getCopyId)
                .filter(Objects::nonNull).toList());
        List<QueueEntry> entries = new ArrayList<>();
        int position = 0;
        for (Reservation reservation : queue) {
            boolean pending = reservation.getStatus() == ReservationStatus.PENDING;
            CopySnapshot copy = reservation.getCopyId() == null ? null : held.get(reservation.getCopyId());
            entries.add(new QueueEntry(reservation.getId(), reservation.getStatus(), pending ? ++position : null,
                    MemberBrief.of(membersById.get(reservation.getMemberId())), reservation.getReservedAt(),
                    reservation.getPickupDeadline(), copy == null ? null : copy.copyNumber()));
        }
        return entries;
    }

    /** US-035 : réservations prêtes, date limite la plus proche d'abord. */
    @Transactional(readOnly = true)
    public Page<ReadyReservation> ready(Pageable pageable) {
        LocalDate today = time.today();
        Page<Reservation> page = reservations.findReady(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        Map<Long, CopySnapshot> held = copies.describe(page.map(Reservation::getCopyId).getContent());
        Map<Long, MemberRef> membersById = members.refs(page.map(Reservation::getMemberId).getContent());
        return page.map(reservation -> {
            CopySnapshot copy = held.get(reservation.getCopyId());
            return new ReadyReservation(reservation.getId(), copy.title(), copy.copyNumber(), copy.shelfMark(),
                    MemberContactBrief.of(membersById.get(reservation.getMemberId())), reservation.getPickupDeadline(),
                    reservation.getPickupDeadline().isEqual(today), "FAILED".equals(reservation.getNotificationStatus()));
        });
    }

    /** US-035, Q-26 : exemplaires libérés depuis le début de la veille ; destination lue sur l'état actuel (Q-42). */
    @Transactional(readOnly = true)
    public Page<ReleasedCopy> released(Pageable pageable) {
        Instant since = time.today().minusDays(1).atStartOfDay(time.zone()).toInstant();
        Page<Reservation> page = reservations.findReleasedSince(RELEASED, since,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        Map<Long, CopySnapshot> released = copies.describe(page.map(Reservation::getCopyId).getContent());
        Map<Long, MemberRef> previous = members.refs(page.map(Reservation::getMemberId).getContent());
        return page.map(reservation -> {
            CopySnapshot copy = released.get(reservation.getCopyId());
            HoldView hold = copy.status() == CopyStatus.RESERVED ? holdOf(copy.id()) : null;
            return new ReleasedCopy(reservation.getId(), copy.title(), copy.copyNumber(), copy.shelfMark(),
                    MemberBrief.of(previous.get(reservation.getMemberId())),
                    LocalDate.ofInstant(reservation.getClosedAt(), time.zone()), reservation.getStatus(),
                    hold == null ? Destination.SHELF : Destination.HOLD, hold);
        });
    }

    /** RG-33 : prochain lot de réservations prêtes non retirées ; chaque exemplaire est réattribué (RG-32). */
    @Transactional
    public int expireNextBatch() {
        List<Reservation> overdue = reservations.findPickupOverdue(time.today(), PageRequest.of(0, BATCH_SIZE));
        for (Reservation reservation : overdue) {
            CopySnapshot copy = copies.lockById(reservation.getCopyId());
            reservation.expire(time.now());
            reservations.flush();
            audit.log(null, AuditAction.STATE_CHANGE, AuditObjectType.RESERVATION, reservation.getId(),
                    Map.of("from", "READY", "to", "EXPIRED", "copyNumber", copy.copyNumber()));
            allocator.allocate(copy, null);
        }
        return overdue.size();
    }

    private HoldView holdOf(long copyId) {
        return reservations.findFirstByCopyIdAndStatus(copyId, ReservationStatus.READY)
                .map(reservation -> {
                    MemberRef member = members.require(reservation.getMemberId());
                    return new HoldView(member.memberNumber(), member.lastName(), member.firstName(),
                            reservation.getPickupDeadline());
                })
                .orElse(null);
    }

    private CurrentReservation current(Reservation reservation) {
        long queueLength = reservations.countByBookIdAndStatus(reservation.getBookId(), ReservationStatus.PENDING);
        Integer position = reservation.getStatus() == ReservationStatus.PENDING
                ? (int) reservations.positionOf(reservation.getBookId(), reservation.getReservedAt(), reservation.getId())
                : null;
        return new CurrentReservation(reservation.getId(), reservation.getStatus(), position, queueLength,
                reservation.getPickupDeadline());
    }

    /** RG-29 → RG-31 et RG-23 hors quota d'emprunts : tous les motifs, dans l'ordre de priorité. */
    private Check check(long bookId, MemberRef member) {
        List<String> reasons = new ArrayList<>();
        BookAvailability availability = copies.availability(bookId);
        if (availability.available() > 0) {
            reasons.add(COPY_AVAILABLE_ON_SHELF);
        } else if (availability.inCirculation() == 0) {
            reasons.add(NO_COPY_IN_CIRCULATION);
        }
        if (reservations.existsByMemberIdAndBookIdAndStatusIn(member.id(), bookId, Reservation.ACTIVE)) {
            reasons.add(RESERVATION_ALREADY_EXISTS);
        }
        if (loans.memberHasBookOnLoan(member.id(), bookId, Loan.ONGOING)) {
            reasons.add(BOOK_ALREADY_BORROWED);
        }
        long active = reservations.countByMemberIdAndStatusIn(member.id(), Reservation.ACTIVE);
        int limit = settings.getInt(SettingCode.RESERVATION_MAX_ACTIVE);
        if (active >= limit) {
            reasons.add(RESERVATION_LIMIT_REACHED);
        }
        Eligibility memberEligibility = eligibility.evaluate(member.id(), member.status(), false);
        if (!memberEligibility.eligible()) {
            reasons.add(MEMBER_NOT_ELIGIBLE);
        }
        return new Check(List.copyOf(reasons), memberEligibility.reasons(), active, limit);
    }

    private static BusinessException refusal(Check check) {
        String first = check.reasons().get(0);
        return switch (first) {
            case COPY_AVAILABLE_ON_SHELF -> BusinessException.of(ErrorCode.COPY_AVAILABLE_ON_SHELF,
                    "Un exemplaire est disponible en rayon : vous pouvez l'emprunter directement.");
            case NO_COPY_IN_CIRCULATION -> BusinessException.of(ErrorCode.NO_COPY_IN_CIRCULATION,
                    "Document indisponible : aucun exemplaire ne peut être réservé.");
            case RESERVATION_ALREADY_EXISTS -> BusinessException.of(ErrorCode.RESERVATION_ALREADY_EXISTS,
                    "Une réservation est déjà en cours pour ce livre.");
            case BOOK_ALREADY_BORROWED -> BusinessException.of(ErrorCode.BOOK_ALREADY_BORROWED,
                    "Ce livre est déjà en cours d'emprunt.");
            case RESERVATION_LIMIT_REACHED -> BusinessException.of(ErrorCode.RESERVATION_LIMIT_REACHED,
                            "Nombre maximal de réservations atteint (" + check.limit() + ").")
                    .withProperty("limit", check.limit());
            default -> BusinessException.of(ErrorCode.MEMBER_NOT_ELIGIBLE, "Réservation impossible.")
                    .withReasons(check.memberReasons());
        };
    }

    private record Check(List<String> reasons, List<String> memberReasons, long activeReservations, int limit) {
    }
}
