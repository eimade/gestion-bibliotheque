package com.bibliotheque.circulation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.service.CopyCirculationService;
import com.bibliotheque.catalog.service.CopyCirculationService.CopySnapshot;
import com.bibliotheque.circulation.domain.Fine;
import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.Reservation;
import com.bibliotheque.circulation.domain.ReservationStatus;
import com.bibliotheque.circulation.repository.FineRepository;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.circulation.repository.ReservationRepository;
import com.bibliotheque.circulation.service.CirculationViews.CopyRelease;
import com.bibliotheque.circulation.service.CirculationViews.FineBrief;
import com.bibliotheque.circulation.service.CirculationViews.LoanResult;
import com.bibliotheque.circulation.service.CirculationViews.LossAmounts;
import com.bibliotheque.circulation.service.CirculationViews.MemberBrief;
import com.bibliotheque.circulation.service.CirculationViews.MemberLoan;
import com.bibliotheque.circulation.service.CirculationViews.RenewalView;
import com.bibliotheque.circulation.service.CirculationViews.ReturnResult;
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

/**
 * Emprunts au guichet (US-025 → US-030) : chaque opération est une transaction unique qui verrouille
 * l'adhérent puis l'exemplaire (ARCH-circulation §5).
 */
@Service
public class LoanService {

    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final FineRepository fines;
    private final CopyCirculationService copies;
    private final MemberLookup members;
    private final EligibilityService eligibility;
    private final LoanRenewalPolicy renewalPolicy;
    private final ReservationAllocator allocator;
    private final FineCalculator calculator;
    private final SettingsService settings;
    private final CirculationAudit audit;
    private final BusinessTime time;

    public LoanService(LoanRepository loans, ReservationRepository reservations, FineRepository fines,
                       CopyCirculationService copies, MemberLookup members, EligibilityService eligibility,
                       LoanRenewalPolicy renewalPolicy, ReservationAllocator allocator, FineCalculator calculator,
                       SettingsService settings, CirculationAudit audit, BusinessTime time) {
        this.loans = loans;
        this.reservations = reservations;
        this.fines = fines;
        this.copies = copies;
        this.members = members;
        this.eligibility = eligibility;
        this.renewalPolicy = renewalPolicy;
        this.allocator = allocator;
        this.calculator = calculator;
        this.settings = settings;
        this.audit = audit;
        this.time = time;
    }

    /** US-025 : RG-21 → RG-24 ; une réservation prête du même adhérent est honorée (AC-025-2). */
    @Transactional
    public LoanResult borrow(String memberNumber, String copyNumber, long agentAccountId) {
        MemberRef member = members.lock(memberNumber);
        Eligibility before = eligibility.evaluate(member.id(), member.status(), true);
        if (!before.eligible()) {
            throw BusinessException.of(ErrorCode.MEMBER_NOT_ELIGIBLE, "Emprunt impossible.")
                    .withReasons(before.reasons());
        }
        CopySnapshot copy = copies.lockByNumber(copyNumber);
        boolean reservationFulfilled = false;
        if (copy.status() == CopyStatus.RESERVED) {
            Reservation held = heldReservation(copy, agentAccountId);
            if (held == null) {
                copy = copies.lockById(copy.id());
            } else if (held.getMemberId() == member.id()) {
                held.fulfil(time.now(), agentAccountId);
                reservationFulfilled = true;
                audit.log(agentAccountId, AuditAction.STATE_CHANGE, AuditObjectType.RESERVATION, held.getId(),
                        Map.of("from", "READY", "to", "FULFILLED", "copyNumber", copyNumber));
            } else {
                throw BusinessException.of(ErrorCode.COPY_RESERVED_FOR_ANOTHER_MEMBER,
                        "Exemplaire réservé pour un autre adhérent.");
            }
        }
        if (!reservationFulfilled) {
            requireBorrowable(copy);
        }
        LocalDate today = time.today();
        LocalDate dueDate = today.plusDays(settings.getInt(SettingCode.LOAN_DURATION_DAYS));
        Loan loan = loans.save(new Loan(copy.id(), member.id(), today, dueDate, agentAccountId));
        copies.changeStatus(copy.id(), CopyStatus.ON_LOAN);
        loans.flush();
        audit.log(agentAccountId, AuditAction.CREATE, AuditObjectType.LOAN, loan.getId(), Map.of(
                "memberNumber", memberNumber, "copyNumber", copyNumber, "dueDate", dueDate.toString()));
        return new LoanResult(loan.getId(), copyNumber, copy.title(), dueDate, reservationFulfilled,
                eligibility.evaluate(member.id(), member.status(), true));
    }

    /** US-026 : retour, attribution à la file (RG-32) et pénalité de retard (RG-36) dans la même transaction. */
    @Transactional
    public ReturnResult giveBack(String copyNumber, long agentAccountId) {
        CopySnapshot copy = copies.lockByNumber(copyNumber);
        Loan loan = loans.findFirstByCopyIdAndStatusIn(copy.id(), Loan.ONGOING)
                .orElseThrow(() -> BusinessException.of(ErrorCode.NO_ACTIVE_LOAN_FOR_COPY,
                        "Aucun emprunt en cours pour cet exemplaire."));
        LocalDate today = time.today();
        int lateDays = loan.overdueDays(today);
        loan.giveBack(today, agentAccountId);
        FineBrief fine = createLateFine(loan, lateDays, agentAccountId);
        loans.flush();
        CopyRelease release = allocator.allocate(copy, agentAccountId);
        MemberRef member = members.require(loan.getMemberId());
        audit.log(agentAccountId, AuditAction.STATE_CHANGE, AuditObjectType.LOAN, loan.getId(), Map.of(
                "to", "RETURNED", "copyNumber", copyNumber, "memberNumber", member.memberNumber(),
                "lateDays", lateDays));
        return new ReturnResult(copyNumber, copy.title(), copy.shelfMark(), MemberBrief.of(member), lateDays, fine,
                release.destination(), release.hold());
    }

    /**
     * US-027 : prolongation par l'adhérent ({@code ownerMemberId} renseigné) ou au guichet. Un emprunt d'un autre
     * adhérent est présenté comme introuvable (AC-027-6).
     */
    @Transactional
    public MemberLoan renew(long loanId, Long ownerMemberId, long actorAccountId) {
        Loan loan = loans.findById(loanId)
                .filter(found -> ownerMemberId == null || ownerMemberId.equals(found.getMemberId()))
                .orElseThrow(() -> BusinessException.of(ErrorCode.LOAN_NOT_FOUND, "Emprunt introuvable."));
        if (!loan.isOngoing()) {
            throw BusinessException.of(ErrorCode.LOAN_NOT_ACTIVE, "Cet emprunt n'est plus en cours.");
        }
        MemberRef member = members.lock(loan.getMemberId());
        CopySnapshot copy = copies.describe(List.of(loan.getCopyId())).get(loan.getCopyId());
        LocalDate today = time.today();
        Eligibility memberEligibility = eligibility.evaluate(member.id(), member.status(), false);
        boolean reserved = reservations.existsByBookIdAndStatus(copy.bookId(), ReservationStatus.PENDING);
        RenewalView renewal = renewalPolicy.evaluate(loan, memberEligibility, reserved, today);
        if (!renewal.allowed()) {
            throw renewalRefusal(renewal);
        }
        LocalDate previous = loan.getDueDate();
        loan.renew(renewal.newDueDate());
        loans.flush();
        audit.log(actorAccountId, AuditAction.UPDATE, AuditObjectType.LOAN, loan.getId(), Map.of(
                "event", "RENEWAL", "copyNumber", copy.copyNumber(), "memberNumber", member.memberNumber(),
                "before", previous.toString(), "after", loan.getDueDate().toString()));
        return CirculationReadService.memberLoan(loan, copy, today, calculator,
                renewalPolicy.evaluate(loan, memberEligibility, reserved, today));
    }

    /** ECR-27 : montants qui seraient créés par la déclaration de perte (RG-36, RG-37). */
    @Transactional(readOnly = true)
    public LossAmounts lossPreview(long loanId) {
        Loan loan = ongoing(loanId);
        return lossAmounts(loan.overdueDays(time.today()));
    }

    /** US-029, RG-28 : emprunt et exemplaire perdus, pénalités de retard et de perte créées. */
    @Transactional
    public LossAmounts declareLost(long loanId, long agentAccountId) {
        Loan loan = ongoing(loanId);
        CopySnapshot copy = copies.lockById(loan.getCopyId());
        LocalDate today = time.today();
        int lateDays = loan.overdueDays(today);
        LossAmounts amounts = lossAmounts(lateDays);
        loan.declareLost(today);
        createLateFine(loan, lateDays, agentAccountId);
        if (amounts.lostItemFine().signum() > 0) {
            Fine lost = fines.save(Fine.lostItem(loan.getMemberId(), loan.getId(), amounts.lostItemFine(), time.now()));
            logFineCreated(lost, agentAccountId);
        }
        copies.changeStatus(copy.id(), CopyStatus.LOST);
        loans.flush();
        audit.log(agentAccountId, AuditAction.STATE_CHANGE, AuditObjectType.LOAN, loan.getId(), Map.of(
                "to", "LOST", "copyNumber", copy.copyNumber(), "lateDays", lateDays));
        return amounts;
    }

    /** US-030 : un exemplaire perdu retrouvé est remis en circulation ; les pénalités restent dues (RG-28). */
    @Transactional
    public CopyRelease markFound(String copyNumber, long agentAccountId) {
        CopySnapshot copy = copies.lockByNumber(copyNumber);
        if (copy.status() != CopyStatus.LOST) {
            throw BusinessException.of(ErrorCode.COPY_NOT_LOST, "Cet exemplaire n'est pas déclaré perdu.");
        }
        CopyRelease release = allocator.allocate(copy, agentAccountId);
        audit.log(agentAccountId, AuditAction.STATE_CHANGE, AuditObjectType.COPY, copyNumber,
                Map.of("from", "LOST", "to", release.destination() == CirculationViews.Destination.HOLD
                        ? "RESERVED" : "AVAILABLE"));
        return release;
    }

    /**
     * Réservation prête pour laquelle l'exemplaire est mis de côté. Une réservation dont la date limite est
     * passée est expirée sur-le-champ et l'exemplaire réattribué (ADR-006 §3) : renvoie alors {@code null}.
     */
    private Reservation heldReservation(CopySnapshot copy, long agentAccountId) {
        Optional<Reservation> held = reservations.findFirstByCopyIdAndStatus(copy.id(), ReservationStatus.READY);
        if (held.isEmpty()) {
            return null;
        }
        Reservation reservation = held.get();
        if (!reservation.isPickupOverdue(time.today())) {
            return reservation;
        }
        reservation.expire(time.now());
        reservations.flush();
        audit.log(null, AuditAction.STATE_CHANGE, AuditObjectType.RESERVATION, reservation.getId(),
                Map.of("from", "READY", "to", "EXPIRED", "copyNumber", copy.copyNumber()));
        allocator.allocate(copy, agentAccountId);
        Optional<Reservation> next = reservations.findFirstByCopyIdAndStatus(copy.id(), ReservationStatus.READY);
        return next.orElse(null);
    }

    /** AC-025-9 : messages exacts d'UX-emprunts selon le statut de l'exemplaire. */
    private static void requireBorrowable(CopySnapshot copy) {
        String message = switch (copy.status()) {
            case AVAILABLE -> null;
            case ON_LOAN -> "Exemplaire déjà emprunté.";
            case LOST -> "Exemplaire déclaré perdu : il ne peut pas être prêté.";
            case WITHDRAWN -> "Exemplaire retiré du fonds : il ne peut pas être prêté.";
            case RESERVED -> "Exemplaire réservé pour un autre adhérent.";
        };
        if (message != null) {
            throw BusinessException.of(ErrorCode.COPY_NOT_AVAILABLE, message)
                    .withProperty("copyStatus", copy.status().name());
        }
    }

    private FineBrief createLateFine(Loan loan, int lateDays, long agentAccountId) {
        BigDecimal amount = calculator.lateFine(lateDays);
        if (amount.signum() <= 0) {
            return null;
        }
        Fine fine = fines.save(Fine.lateReturn(loan.getMemberId(), loan.getId(), lateDays, amount, time.now()));
        logFineCreated(fine, agentAccountId);
        return new FineBrief(fine.getId(), fine.getAmount());
    }

    private void logFineCreated(Fine fine, long agentAccountId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", fine.getReason().name());
        details.put("amount", fine.getAmount());
        details.put("loanId", fine.getLoanId());
        if (fine.getLateDays() != null) {
            details.put("lateDays", fine.getLateDays());
        }
        audit.log(agentAccountId, AuditAction.CREATE, AuditObjectType.FINE, fine.getId(), details);
    }

    private LossAmounts lossAmounts(int lateDays) {
        BigDecimal late = calculator.lateFine(lateDays);
        BigDecimal lost = calculator.lostItemAmount();
        return new LossAmounts(lateDays, late, lost, late.add(lost));
    }

    private Loan ongoing(long loanId) {
        Loan loan = loans.findById(loanId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.LOAN_NOT_FOUND, "Emprunt introuvable."));
        if (!loan.isOngoing()) {
            throw BusinessException.of(ErrorCode.LOAN_NOT_ACTIVE, "Cet emprunt n'est plus en cours.");
        }
        return loan;
    }

    private static BusinessException renewalRefusal(RenewalView renewal) {
        return switch (renewal.blockedBy()) {
            case LoanRenewalPolicy.LOAN_OVERDUE -> BusinessException.of(ErrorCode.LOAN_OVERDUE,
                    "Prolongation impossible : emprunt en retard.");
            case LoanRenewalPolicy.RENEWAL_LIMIT_REACHED -> BusinessException.of(ErrorCode.RENEWAL_LIMIT_REACHED,
                    "Prolongation impossible : nombre maximal de prolongations atteint.");
            case LoanRenewalPolicy.RENEWAL_BLOCKED_BY_RESERVATION -> BusinessException.of(
                    ErrorCode.RENEWAL_BLOCKED_BY_RESERVATION,
                    "Prolongation impossible : document réservé par un autre adhérent.");
            default -> BusinessException.of(ErrorCode.MEMBER_NOT_ELIGIBLE, "Prolongation impossible.")
                    .withReasons(renewal.memberReasons());
        };
    }
}
