package com.bibliotheque.circulation.service;

import static com.bibliotheque.circulation.CirculationFixtures.BOOK_ID;
import static com.bibliotheque.member.MemberFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.service.CopyCirculationService;
import com.bibliotheque.catalog.service.CopyCirculationService.BookAvailability;
import com.bibliotheque.circulation.CirculationFixtures;
import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.Reservation;
import com.bibliotheque.circulation.domain.ReservationStatus;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.circulation.repository.ReservationRepository;
import com.bibliotheque.circulation.service.CirculationViews.CopyRelease;
import com.bibliotheque.circulation.service.CirculationViews.Destination;
import com.bibliotheque.circulation.service.CirculationViews.HoldView;
import com.bibliotheque.circulation.service.CirculationViews.MemberReservation;
import com.bibliotheque.circulation.service.CirculationViews.QueueEntry;
import com.bibliotheque.circulation.service.CirculationViews.ReadyReservation;
import com.bibliotheque.circulation.service.CirculationViews.ReleasedCopy;
import com.bibliotheque.circulation.service.CirculationViews.ReservationCancelled;
import com.bibliotheque.circulation.service.CirculationViews.ReservationCreated;
import com.bibliotheque.circulation.service.CirculationViews.ReservationPreview;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.member.MemberFixtures;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberCirculation.Eligibility;
import com.bibliotheque.member.service.MemberLookup;
import com.bibliotheque.support.TestFixtures;
import com.bibliotheque.support.TestSettings;

class ReservationServiceTest {

    private static final long MEMBER = 1L;
    private static final long ACTOR = 50L;

    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final LoanRepository loans = mock(LoanRepository.class);
    private final CopyCirculationService copies = mock(CopyCirculationService.class);
    private final MemberLookup members = mock(MemberLookup.class);
    private final EligibilityService eligibility = mock(EligibilityService.class);
    private final ReservationAllocator allocator = mock(ReservationAllocator.class);
    private final CirculationAudit audit = mock(CirculationAudit.class);
    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservations, loans, copies, members, eligibility, allocator,
                TestSettings.defaults(), audit, TestSettings.time());
        when(members.lock(MEMBER)).thenReturn(MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE));
        when(members.lock("A000001")).thenReturn(MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE));
        when(members.require(anyLong())).thenAnswer(invocation ->
                MemberFixtures.ref(invocation.getArgument(0), MemberStatus.ACTIVE));
        when(copies.titleOf(BOOK_ID)).thenReturn("Bel-Ami");
        when(copies.availability(BOOK_ID)).thenReturn(new BookAvailability(0, 2));
        when(eligibility.evaluate(anyLong(), any(), anyBoolean())).thenReturn(MemberFixtures.eligible());
        when(reservations.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 300L);
            return saved;
        });
    }

    @Test
    void reservationAccepteeEnPositionDeux() {
        when(reservations.positionOf(eq(BOOK_ID), any(), eq(300L))).thenReturn(2L);
        when(reservations.countByBookIdAndStatus(BOOK_ID, ReservationStatus.PENDING)).thenReturn(2L);

        ReservationCreated created = service.create(BOOK_ID, MEMBER, ACTOR);

        assertThat(created.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(created.position()).isEqualTo(2L);
        assertThat(service.create(BOOK_ID, "A000001", 2L).queueLength()).isEqualTo(2L);
    }

    @Test
    void refusSelonLaDisponibiliteEtLeQuota() {
        when(copies.availability(BOOK_ID)).thenReturn(new BookAvailability(1, 1), new BookAvailability(0, 0),
                new BookAvailability(0, 1));
        when(reservations.countByMemberIdAndStatusIn(MEMBER, Reservation.ACTIVE)).thenReturn(0L, 0L, 3L);

        assertRefused(ErrorCode.COPY_AVAILABLE_ON_SHELF);
        assertRefused(ErrorCode.NO_COPY_IN_CIRCULATION);
        assertThatThrownBy(() -> service.create(BOOK_ID, MEMBER, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.RESERVATION_LIMIT_REACHED);
                    assertThat(e.getMessage()).isEqualTo("Nombre maximal de réservations atteint (3).");
                });
        verify(reservations, never()).save(any());
    }

    @Test
    void refusDoublonEmprunteOuNonEligible() {
        when(reservations.existsByMemberIdAndBookIdAndStatusIn(MEMBER, BOOK_ID, Reservation.ACTIVE))
                .thenReturn(true, false, false);
        when(loans.memberHasBookOnLoan(MEMBER, BOOK_ID, Loan.ONGOING)).thenReturn(false, true, false);
        when(eligibility.evaluate(MEMBER, MemberStatus.ACTIVE, false)).thenReturn(MemberFixtures.eligible(),
                MemberFixtures.eligible(), new Eligibility(false, List.of("MEMBER_SUSPENDED"), 0, 5, 5,
                        BigDecimal.ZERO, new BigDecimal("5.00")));

        assertRefused(ErrorCode.RESERVATION_ALREADY_EXISTS);
        assertRefused(ErrorCode.BOOK_ALREADY_BORROWED);
        assertThatThrownBy(() -> service.create(BOOK_ID, MEMBER, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_NOT_ELIGIBLE);
                    assertThat(e.reasons()).containsExactly("MEMBER_SUSPENDED");
                });
    }

    @Test
    void previsualisationListeTousLesMotifsEtLaPosition() {
        when(copies.availability(BOOK_ID)).thenReturn(new BookAvailability(1, 1));
        when(reservations.countByBookIdAndStatus(BOOK_ID, ReservationStatus.PENDING)).thenReturn(1L);
        when(reservations.countByMemberIdAndStatusIn(MEMBER, Reservation.ACTIVE)).thenReturn(3L);

        ReservationPreview preview = service.preview(BOOK_ID, MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE), true);

        assertThat(preview.reservable()).isFalse();
        assertThat(preview.reasons()).containsExactly("COPY_AVAILABLE_ON_SHELF", "RESERVATION_LIMIT_REACHED");
        assertThat(preview.position()).isEqualTo(2L);
        assertThat(preview.limit()).isEqualTo(3);
        assertThat(preview.pickupDays()).isEqualTo(3);
        assertThat(preview.member().memberNumber()).isEqualTo("A000001");
    }

    @Test
    void situationDeLAdherentSurUneNotice() {
        Reservation mine = CirculationFixtures.pending(10L, MEMBER, TestFixtures.NOW);
        when(reservations.findFirstByMemberIdAndBookIdAndStatusIn(MEMBER, BOOK_ID, Reservation.ACTIVE))
                .thenReturn(Optional.of(mine));
        when(reservations.positionOf(BOOK_ID, TestFixtures.NOW, 10L)).thenReturn(2L);
        when(reservations.countByBookIdAndStatus(BOOK_ID, ReservationStatus.PENDING)).thenReturn(3L);

        var situation = service.myBookReservation(BOOK_ID, MEMBER);

        assertThat(situation.current().position()).isEqualTo(2);
        assertThat(situation.current().queueLength()).isEqualTo(3L);
        assertThat(situation.preview().member()).isNull();
    }

    @Test
    void annulationDUneReservationEnAttente() {
        Reservation pending = CirculationFixtures.pending(10L, MEMBER, TestFixtures.NOW);
        when(reservations.findById(10L)).thenReturn(Optional.of(pending));

        ReservationCancelled cancelled = service.cancel(10L, MEMBER, ACTOR);

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.release()).isNull();
        verify(allocator, never()).allocate(any(), any());
    }

    @Test
    void annulationDUneReservationPreteReattribueLExemplaire() {
        Reservation ready = CirculationFixtures.ready(10L, MEMBER, 20L, TODAY.plusDays(2));
        when(reservations.findById(10L)).thenReturn(Optional.of(ready));
        when(copies.lockById(20L)).thenReturn(CirculationFixtures.copy(20L, CopyStatus.RESERVED));
        CopyRelease release = new CopyRelease("00000020", "R CAM", Destination.HOLD,
                new HoldView("A000002", "Petit", "Léa", TODAY.plusDays(3)));
        when(allocator.allocate(any(), eq(2L))).thenReturn(release);

        assertThat(service.cancel(10L, null, 2L).release()).isEqualTo(release);
    }

    @Test
    void annulationRefuseeSiCloseOuDUnAutreAdherent() {
        Reservation closed = CirculationFixtures.pending(10L, MEMBER, TestFixtures.NOW);
        closed.cancel(TestFixtures.NOW, ACTOR);
        when(reservations.findById(10L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.cancel(10L, MEMBER, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.RESERVATION_CLOSED);
                    assertThat(e.getMessage()).isEqualTo("Cette réservation est déjà close.");
                });
        assertThatThrownBy(() -> service.cancel(10L, 99L, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.RESERVATION_NOT_FOUND));
    }

    @Test
    void fileDAttenteLaPreteDAbordPuisLesPositions() {
        Reservation ready = CirculationFixtures.ready(10L, 3L, 20L, TODAY.plusDays(2));
        Reservation first = CirculationFixtures.pending(11L, 1L, TestFixtures.NOW);
        Reservation second = CirculationFixtures.pending(12L, 2L, TestFixtures.NOW.plusSeconds(60));
        when(reservations.findQueue(BOOK_ID, Reservation.ACTIVE)).thenReturn(List.of(ready, first, second));
        when(members.refs(any())).thenReturn(Map.of(1L, MemberFixtures.ref(1L, MemberStatus.ACTIVE),
                2L, MemberFixtures.ref(2L, MemberStatus.ACTIVE), 3L, MemberFixtures.ref(3L, MemberStatus.ACTIVE)));
        when(copies.describe(List.of(20L))).thenReturn(Map.of(20L, CirculationFixtures.copy(20L, CopyStatus.RESERVED)));

        List<QueueEntry> queue = service.queue(BOOK_ID);

        assertThat(queue).extracting(QueueEntry::position).containsExactly(null, 1, 2);
        assertThat(queue.get(0).copyNumber()).isEqualTo("00000020");
    }

    @Test
    void reservationsDUnAdherentAvecPosition() {
        Reservation pending = CirculationFixtures.pending(11L, MEMBER, TestFixtures.NOW);
        Reservation ready = CirculationFixtures.ready(12L, MEMBER, 20L, TODAY.plusDays(2));
        when(reservations.findByMemberIdAndStatusInOrderByReservedAtAscIdAsc(MEMBER, Reservation.ACTIVE))
                .thenReturn(List.of(pending, ready));
        when(copies.titlesOf(List.of(BOOK_ID))).thenReturn(Map.of(BOOK_ID, "Bel-Ami"));
        when(copies.describe(List.of(20L))).thenReturn(Map.of(20L, CirculationFixtures.copy(20L, CopyStatus.RESERVED)));
        when(reservations.positionOf(BOOK_ID, TestFixtures.NOW, 11L)).thenReturn(1L);

        List<MemberReservation> mine = service.memberReservations(MEMBER);

        assertThat(mine).extracting(MemberReservation::position).containsExactly(1, null);
        assertThat(mine.get(1).copyNumber()).isEqualTo("00000020");
    }

    @Test
    void listesATraiterAuGuichet() {
        Reservation ready = CirculationFixtures.ready(12L, MEMBER, 20L, TODAY);
        Reservation expired = CirculationFixtures.ready(13L, 2L, 21L, TODAY.minusDays(1));
        expired.expire(TestFixtures.NOW);
        when(reservations.findReady(any())).thenReturn(new PageImpl<>(List.of(ready)));
        when(reservations.findReleasedSince(any(), any(), any())).thenReturn(new PageImpl<>(List.of(expired)));
        when(copies.describe(List.of(20L))).thenReturn(Map.of(20L, CirculationFixtures.copy(20L, CopyStatus.RESERVED)));
        when(copies.describe(List.of(21L))).thenReturn(Map.of(21L, CirculationFixtures.copy(21L, CopyStatus.RESERVED)));
        when(members.refs(any())).thenReturn(Map.of(MEMBER, MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE),
                2L, MemberFixtures.ref(2L, MemberStatus.ACTIVE)));
        when(reservations.findFirstByCopyIdAndStatus(21L, ReservationStatus.READY))
                .thenReturn(Optional.of(CirculationFixtures.ready(14L, 3L, 21L, TODAY.plusDays(3))));

        ReadyReservation readyRow = service.ready(PageRequest.of(0, 20)).getContent().get(0);
        ReleasedCopy releasedRow = service.released(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(readyRow.lastDay()).isTrue();
        assertThat(readyRow.member().phone()).isEqualTo("0612345678");
        assertThat(releasedRow.destination()).isEqualTo(Destination.HOLD);
        assertThat(releasedRow.hold().memberNumber()).isEqualTo("A000003");
        assertThat(releasedRow.closedStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void expirationDesMisesDeCoteEchues() {
        Reservation stale = CirculationFixtures.ready(12L, MEMBER, 20L, TODAY.minusDays(1));
        when(reservations.findPickupOverdue(eq(TODAY), any())).thenReturn(List.of(stale));
        when(copies.lockById(20L)).thenReturn(CirculationFixtures.copy(20L, CopyStatus.RESERVED));

        assertThat(service.expireNextBatch()).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        verify(allocator).allocate(any(), isNull());
    }

    private void assertRefused(ErrorCode code) {
        assertThatThrownBy(() -> service.create(BOOK_ID, MEMBER, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
