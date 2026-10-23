package com.bibliotheque.circulation.service;

import static com.bibliotheque.member.MemberFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.service.CopyCirculationService;
import com.bibliotheque.circulation.CirculationFixtures;
import com.bibliotheque.circulation.domain.Fine;
import com.bibliotheque.circulation.domain.FineReason;
import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.LoanStatus;
import com.bibliotheque.circulation.domain.Reservation;
import com.bibliotheque.circulation.domain.ReservationStatus;
import com.bibliotheque.circulation.repository.FineRepository;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.circulation.repository.ReservationRepository;
import com.bibliotheque.circulation.service.CirculationViews.CopyRelease;
import com.bibliotheque.circulation.service.CirculationViews.Destination;
import com.bibliotheque.circulation.service.CirculationViews.HoldView;
import com.bibliotheque.circulation.service.CirculationViews.LoanResult;
import com.bibliotheque.circulation.service.CirculationViews.LossAmounts;
import com.bibliotheque.circulation.service.CirculationViews.MemberLoan;
import com.bibliotheque.circulation.service.CirculationViews.ReturnResult;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.member.MemberFixtures;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberCirculation.Eligibility;
import com.bibliotheque.member.service.MemberLookup;
import com.bibliotheque.support.TestSettings;

class LoanServiceTest {

    private static final long AGENT = 2L;
    private static final long MEMBER = 1L;

    private final LoanRepository loans = mock(LoanRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final FineRepository fines = mock(FineRepository.class);
    private final CopyCirculationService copies = mock(CopyCirculationService.class);
    private final MemberLookup members = mock(MemberLookup.class);
    private final EligibilityService eligibility = mock(EligibilityService.class);
    private final ReservationAllocator allocator = mock(ReservationAllocator.class);
    private final CirculationAudit audit = mock(CirculationAudit.class);
    private LoanService service;

    @BeforeEach
    void setUp() {
        service = new LoanService(loans, reservations, fines, copies, members, eligibility,
                new LoanRenewalPolicy(TestSettings.defaults()), allocator, new FineCalculator(TestSettings.defaults()),
                TestSettings.defaults(), audit, TestSettings.time());
        when(members.lock("A000001")).thenReturn(MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE));
        when(members.lock(MEMBER)).thenReturn(MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE));
        when(members.require(anyLong())).thenAnswer(invocation ->
                MemberFixtures.ref(invocation.getArgument(0), MemberStatus.ACTIVE));
        when(eligibility.evaluate(anyLong(), any(), anyBoolean())).thenReturn(MemberFixtures.eligible());
        when(loans.save(any(Loan.class))).thenAnswer(invocation -> {
            Loan saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 900L);
            return saved;
        });
        when(fines.save(any(Fine.class))).thenAnswer(invocation -> {
            Fine saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 70L);
            return saved;
        });
    }

    @Test
    void empruntDUnExemplaireDisponibleAvecEcheanceAVingtEtUnJours() {
        when(copies.lockByNumber("00000010")).thenReturn(CirculationFixtures.copy(10L, CopyStatus.AVAILABLE));

        LoanResult result = service.borrow("A000001", "00000010", AGENT);

        assertThat(result.dueDate()).isEqualTo(TODAY.plusDays(21));
        assertThat(result.reservationFulfilled()).isFalse();
        verify(copies).changeStatus(10L, CopyStatus.ON_LOAN);
    }

    @Test
    void adherentNonEligibleAvecTousSesMotifs() {
        when(eligibility.evaluate(MEMBER, MemberStatus.ACTIVE, true)).thenReturn(new Eligibility(false,
                List.of("OVERDUE_LOAN", "UNPAID_FINES_ABOVE_THRESHOLD"), 2, 5, 3, new BigDecimal("6.00"),
                new BigDecimal("5.00")));

        assertThatThrownBy(() -> service.borrow("A000001", "00000010", AGENT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_NOT_ELIGIBLE);
                    assertThat(e.reasons()).containsExactly("OVERDUE_LOAN", "UNPAID_FINES_ABOVE_THRESHOLD");
                });
        verify(copies, never()).lockByNumber(any());
    }

    @Test
    void exemplaireNonEmpruntableSelonSonStatut() {
        when(copies.lockByNumber("00000011")).thenReturn(CirculationFixtures.copy(11L, CopyStatus.ON_LOAN));
        when(copies.lockByNumber("00000012")).thenReturn(CirculationFixtures.copy(12L, CopyStatus.LOST));
        when(copies.lockByNumber("00000013")).thenReturn(CirculationFixtures.copy(13L, CopyStatus.WITHDRAWN));

        assertRefused("00000011", ErrorCode.COPY_NOT_AVAILABLE, "Exemplaire déjà emprunté.");
        assertRefused("00000012", ErrorCode.COPY_NOT_AVAILABLE, "Exemplaire déclaré perdu : il ne peut pas être prêté.");
        assertRefused("00000013", ErrorCode.COPY_NOT_AVAILABLE,
                "Exemplaire retiré du fonds : il ne peut pas être prêté.");
    }

    @Test
    void empruntDeLExemplaireMisDeCotePourCetAdherent() {
        Reservation held = CirculationFixtures.ready(5L, MEMBER, 10L, TODAY.plusDays(2));
        when(copies.lockByNumber("00000010")).thenReturn(CirculationFixtures.copy(10L, CopyStatus.RESERVED));
        when(reservations.findFirstByCopyIdAndStatus(10L, ReservationStatus.READY)).thenReturn(Optional.of(held));

        LoanResult result = service.borrow("A000001", "00000010", AGENT);

        assertThat(result.reservationFulfilled()).isTrue();
        assertThat(held.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
    }

    @Test
    void retraitLeDernierJourAccepte() {
        Reservation held = CirculationFixtures.ready(5L, MEMBER, 10L, TODAY);
        when(copies.lockByNumber("00000010")).thenReturn(CirculationFixtures.copy(10L, CopyStatus.RESERVED));
        when(reservations.findFirstByCopyIdAndStatus(10L, ReservationStatus.READY)).thenReturn(Optional.of(held));

        assertThat(service.borrow("A000001", "00000010", AGENT).reservationFulfilled()).isTrue();
    }

    @Test
    void exemplaireMisDeCotePourUnAutre() {
        Reservation held = CirculationFixtures.ready(5L, 99L, 10L, TODAY.plusDays(2));
        when(copies.lockByNumber("00000010")).thenReturn(CirculationFixtures.copy(10L, CopyStatus.RESERVED));
        when(reservations.findFirstByCopyIdAndStatus(10L, ReservationStatus.READY)).thenReturn(Optional.of(held));

        assertRefused("00000010", ErrorCode.COPY_RESERVED_FOR_ANOTHER_MEMBER, "Exemplaire réservé pour un autre adhérent.");
    }

    @Test
    void miseDeCoteEchueExpireeEtExemplaireRemisEnRayonAvantLEmprunt() {
        Reservation stale = CirculationFixtures.ready(5L, 99L, 10L, TODAY.minusDays(1));
        when(copies.lockByNumber("00000010")).thenReturn(CirculationFixtures.copy(10L, CopyStatus.RESERVED));
        when(copies.lockById(10L)).thenReturn(CirculationFixtures.copy(10L, CopyStatus.AVAILABLE));
        when(reservations.findFirstByCopyIdAndStatus(10L, ReservationStatus.READY))
                .thenReturn(Optional.of(stale)).thenReturn(Optional.empty());

        LoanResult result = service.borrow("A000001", "00000010", AGENT);

        assertThat(stale.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(result.reservationFulfilled()).isFalse();
        verify(allocator).allocate(any(), eq(AGENT));
    }

    @Test
    void retourALHeureSansReservationNiPenalite() {
        Loan loan = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(19), TODAY.plusDays(2));
        stubReturn(loan, new CopyRelease("00000010", "R CAM", Destination.SHELF, null));

        ReturnResult result = service.giveBack("00000010", AGENT);

        assertThat(result.destination()).isEqualTo(Destination.SHELF);
        assertThat(result.fine()).isNull();
        assertThat(result.lateDays()).isZero();
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.RETURNED);
        verify(fines, never()).save(any());
    }

    @Test
    void retourLeJourDeLEcheanceSansPenalite() {
        Loan loan = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(21), TODAY);
        stubReturn(loan, new CopyRelease("00000010", "R CAM", Destination.SHELF, null));

        assertThat(service.giveBack("00000010", AGENT).fine()).isNull();
    }

    @Test
    void retourEnRetardCreeLaPenaliteEtMetDeCotePourLaFile() {
        Loan loan = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(26), TODAY.minusDays(5));
        HoldView hold = new HoldView("A000789", "Bernard", "Luc", TODAY.plusDays(3));
        stubReturn(loan, new CopyRelease("00000010", "R CAM", Destination.HOLD, hold));

        ReturnResult result = service.giveBack("00000010", AGENT);

        assertThat(result.lateDays()).isEqualTo(5);
        assertThat(result.fine().amount()).isEqualTo(new BigDecimal("1.00"));
        assertThat(result.hold()).isEqualTo(hold);
        assertThat(result.member().memberNumber()).isEqualTo("A000001");
    }

    @Test
    void retourDUnExemplaireNonEmprunte() {
        when(copies.lockByNumber("00000010")).thenReturn(CirculationFixtures.copy(10L, CopyStatus.AVAILABLE));
        when(loans.findFirstByCopyIdAndStatusIn(10L, Loan.ONGOING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.giveBack("00000010", AGENT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.NO_ACTIVE_LOAN_FOR_COPY);
                    assertThat(e.getMessage()).isEqualTo("Aucun emprunt en cours pour cet exemplaire.");
                });
    }

    @Test
    void prolongationParLAdherentOuAuGuichet() {
        Loan loan = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(10), TODAY.plusDays(11));
        when(loans.findById(1L)).thenReturn(Optional.of(loan));
        when(copies.describe(List.of(10L))).thenReturn(Map.of(10L, CirculationFixtures.copy(10L, CopyStatus.ON_LOAN)));

        MemberLoan renewed = service.renew(1L, MEMBER, 50L);

        assertThat(renewed.dueDate()).isEqualTo(TODAY.plusDays(32));
        assertThat(renewed.renewalCount()).isEqualTo(1);
        assertThat(renewed.renewal().blockedBy()).isEqualTo("RENEWAL_LIMIT_REACHED");
        assertThatThrownBy(() -> service.renew(1L, null, AGENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.RENEWAL_LIMIT_REACHED));
    }

    @Test
    void prolongationRefuseeSiReserveeOuDUnAutreAdherent() {
        Loan loan = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(10), TODAY.plusDays(11));
        when(loans.findById(1L)).thenReturn(Optional.of(loan));
        when(copies.describe(List.of(10L))).thenReturn(Map.of(10L, CirculationFixtures.copy(10L, CopyStatus.ON_LOAN)));
        when(reservations.existsByBookIdAndStatus(CirculationFixtures.BOOK_ID, ReservationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.renew(1L, MEMBER, 50L))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.RENEWAL_BLOCKED_BY_RESERVATION);
                    assertThat(e.getMessage()).isEqualTo(
                            "Prolongation impossible : document réservé par un autre adhérent.");
                });
        assertThatThrownBy(() -> service.renew(1L, 99L, 51L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LOAN_NOT_FOUND));
    }

    @Test
    void prolongationRefuseeEnRetardOuAdherentNonEligible() {
        Loan overdue = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(30), TODAY.minusDays(2));
        Loan fresh = CirculationFixtures.loan(2L, 11L, MEMBER, TODAY.minusDays(10), TODAY.plusDays(11));
        when(loans.findById(1L)).thenReturn(Optional.of(overdue));
        when(loans.findById(2L)).thenReturn(Optional.of(fresh));
        when(copies.describe(any())).thenReturn(Map.of(10L, CirculationFixtures.copy(10L, CopyStatus.ON_LOAN),
                11L, CirculationFixtures.copy(11L, CopyStatus.ON_LOAN)));
        when(eligibility.evaluate(MEMBER, MemberStatus.ACTIVE, false)).thenReturn(MemberFixtures.eligible(),
                new Eligibility(false, List.of("MEMBERSHIP_EXPIRED"), 1, 5, 4, BigDecimal.ZERO, new BigDecimal("5")));

        assertThatThrownBy(() -> service.renew(1L, null, AGENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LOAN_OVERDUE));
        assertThatThrownBy(() -> service.renew(2L, null, AGENT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_NOT_ELIGIBLE);
                    assertThat(e.reasons()).containsExactly("MEMBERSHIP_EXPIRED");
                });
    }

    @Test
    void perteAvecRetardCreeDeuxPenalites() {
        Loan loan = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(25), TODAY.minusDays(4));
        when(loans.findById(1L)).thenReturn(Optional.of(loan));
        when(copies.lockById(10L)).thenReturn(CirculationFixtures.copy(10L, CopyStatus.ON_LOAN));

        LossAmounts preview = service.lossPreview(1L);
        LossAmounts declared = service.declareLost(1L, AGENT);

        assertThat(preview).isEqualTo(declared);
        assertThat(declared.lateFine()).isEqualTo(new BigDecimal("0.80"));
        assertThat(declared.total()).isEqualTo(new BigDecimal("20.80"));
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.LOST);
        ArgumentCaptor<Fine> created = ArgumentCaptor.forClass(Fine.class);
        verify(fines, times(2)).save(created.capture());
        assertThat(created.getAllValues()).extracting(Fine::getReason)
                .containsExactly(FineReason.LATE_RETURN, FineReason.LOST_ITEM);
        verify(copies).changeStatus(10L, CopyStatus.LOST);
    }

    @Test
    void perteDUnEmpruntClosRefusee() {
        Loan loan = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(25), TODAY.minusDays(4));
        loan.giveBack(TODAY, AGENT);
        when(loans.findById(1L)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.declareLost(1L, AGENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LOAN_NOT_ACTIVE));
        assertThatThrownBy(() -> service.lossPreview(2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LOAN_NOT_FOUND));
    }

    @Test
    void exemplaireRetrouveRemisEnCirculation() {
        when(copies.lockByNumber("00000010")).thenReturn(CirculationFixtures.copy(10L, CopyStatus.LOST));
        when(copies.lockByNumber("00000011")).thenReturn(CirculationFixtures.copy(11L, CopyStatus.AVAILABLE));
        CopyRelease release = new CopyRelease("00000010", "R CAM", Destination.SHELF, null);
        when(allocator.allocate(any(), eq(AGENT))).thenReturn(release);

        assertThat(service.markFound("00000010", AGENT)).isEqualTo(release);
        assertThatThrownBy(() -> service.markFound("00000011", AGENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.COPY_NOT_LOST));
    }

    private void stubReturn(Loan loan, CopyRelease release) {
        when(copies.lockByNumber("00000010")).thenReturn(CirculationFixtures.copy(10L, CopyStatus.ON_LOAN));
        when(loans.findFirstByCopyIdAndStatusIn(10L, Loan.ONGOING)).thenReturn(Optional.of(loan));
        when(allocator.allocate(any(), eq(AGENT))).thenReturn(release);
    }

    private void assertRefused(String copyNumber, ErrorCode code, String message) {
        assertThatThrownBy(() -> service.borrow("A000001", copyNumber, AGENT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
    }
}
