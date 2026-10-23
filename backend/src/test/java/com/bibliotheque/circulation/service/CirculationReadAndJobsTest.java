package com.bibliotheque.circulation.service;

import static com.bibliotheque.member.MemberFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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

import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.service.CopyCirculationService;
import com.bibliotheque.circulation.CirculationFixtures;
import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.LoanStatus;
import com.bibliotheque.circulation.domain.Reservation;
import com.bibliotheque.circulation.domain.ReservationStatus;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.circulation.repository.ReservationRepository;
import com.bibliotheque.circulation.service.CirculationReadService.OverdueSort;
import com.bibliotheque.circulation.service.CirculationViews.CopyLoanHistoryItem;
import com.bibliotheque.circulation.service.CirculationViews.CopyRelease;
import com.bibliotheque.circulation.service.CirculationViews.Destination;
import com.bibliotheque.circulation.service.CirculationViews.MemberLoan;
import com.bibliotheque.circulation.service.CirculationViews.OverdueLoan;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.settings.SettingsService;
import com.bibliotheque.member.MemberFixtures;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberLookup;
import com.bibliotheque.support.TestFixtures;
import com.bibliotheque.support.TestSettings;

/** Lectures de la circulation, attribution à la file (RG-32) et traitements planifiés (ADR-006). */
class CirculationReadAndJobsTest {

    private static final long MEMBER = 1L;

    private final LoanRepository loans = mock(LoanRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final CopyCirculationService copies = mock(CopyCirculationService.class);
    private final MemberLookup members = mock(MemberLookup.class);
    private final EligibilityService eligibility = mock(EligibilityService.class);
    private final CirculationAudit audit = mock(CirculationAudit.class);
    private final SettingsService settings = TestSettings.defaults();
    private CirculationReadService reads;
    private ReservationAllocator allocator;

    @BeforeEach
    void setUp() {
        reads = new CirculationReadService(loans, reservations, copies, members, eligibility,
                new LoanRenewalPolicy(settings), new FineCalculator(settings), TestSettings.time(),
                TestFixtures.properties());
        allocator = new ReservationAllocator(reservations, copies, members, settings, audit, TestSettings.time());
        when(members.require(anyLong())).thenAnswer(invocation ->
                MemberFixtures.ref(invocation.getArgument(0), MemberStatus.ACTIVE));
        when(eligibility.evaluate(anyLong(), any(), anyBoolean())).thenReturn(MemberFixtures.eligible());
    }

    @Test
    void empruntsEnCoursAvecRetardEtPenaliteProvisoire() {
        Loan late = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(24), TODAY.minusDays(3));
        Loan fresh = CirculationFixtures.loan(2L, 11L, MEMBER, TODAY.minusDays(3), TODAY.plusDays(18));
        when(loans.findByMemberIdAndStatusInOrderByDueDateAscIdAsc(MEMBER, Loan.ONGOING)).thenReturn(List.of(late, fresh));
        when(copies.describe(List.of(10L, 11L))).thenReturn(Map.of(10L, CirculationFixtures.copy(10L, CopyStatus.ON_LOAN),
                11L, CirculationFixtures.copy(11L, CopyStatus.ON_LOAN)));

        List<MemberLoan> result = reads.memberLoans(MEMBER);

        assertThat(result.get(0).status()).isEqualTo(LoanStatus.OVERDUE);
        assertThat(result.get(0).provisionalFine()).isEqualTo(new BigDecimal("0.60"));
        assertThat(result.get(0).renewal().blockedBy()).isEqualTo("LOAN_OVERDUE");
        assertThat(result.get(1).renewal().allowed()).isTrue();
        assertThat(reads.memberLoans(2L)).isEmpty();
    }

    @Test
    void historiqueSurQuatreMois() {
        Loan returned = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(40), TODAY.minusDays(19));
        returned.giveBack(TODAY.minusDays(20), 2L);
        when(loans.findHistory(eq(MEMBER), eq(TODAY.minusMonths(4)), any())).thenReturn(new PageImpl<>(List.of(returned)));
        when(copies.describe(List.of(10L))).thenReturn(Map.of(10L, CirculationFixtures.copy(10L, CopyStatus.AVAILABLE)));

        assertThat(reads.history(MEMBER, PageRequest.of(0, 20)).getContent())
                .singleElement().satisfies(row -> assertThat(row.returnDate()).isEqualTo(TODAY.minusDays(20)));
    }

    @Test
    void retardsDAuMoinsNJoursSelonLeTri() {
        Loan loan = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(51), TODAY.minusDays(30));
        PageImpl<Loan> page = new PageImpl<>(List.of(loan));
        when(loans.findOverdue(eq(Loan.ONGOING), eq(TODAY.minusDays(7)), any())).thenReturn(page);
        when(loans.findOverdueByMemberName(eq(Loan.ONGOING), eq(TODAY.minusDays(1)), any())).thenReturn(page);
        when(copies.describe(List.of(10L))).thenReturn(Map.of(10L, CirculationFixtures.copy(10L, CopyStatus.ON_LOAN)));
        when(members.refs(List.of(MEMBER))).thenReturn(Map.of(MEMBER, MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE)));

        OverdueLoan row = reads.overdue(7, OverdueSort.OVERDUE_DAYS, 0, 20).getContent().get(0);

        assertThat(row.overdueDays()).isEqualTo(30);
        assertThat(row.provisionalFine()).isEqualTo(new BigDecimal("6.00"));
        assertThat(row.member().email()).isEqualTo("jean.martin@exemple.fr");
        assertThat(reads.overdue(7, OverdueSort.DUE_DATE, 0, 20).getContent()).hasSize(1);
        assertThat(reads.overdue(1, OverdueSort.MEMBER_NAME, 0, 20).getContent()).hasSize(1);
    }

    @Test
    void historiqueDUnExemplaireAvecEmprunteurAnonymise() {
        Loan current = CirculationFixtures.loan(2L, 10L, MEMBER, TODAY.minusDays(3), TODAY.plusDays(18));
        Loan old = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusYears(1), TODAY.minusYears(1).plusDays(21));
        old.giveBack(TODAY.minusYears(1).plusDays(10), 2L);
        old.unlinkMember(TestFixtures.NOW);
        when(copies.findByNumber("00000010")).thenReturn(Optional.of(CirculationFixtures.copy(10L, CopyStatus.ON_LOAN)));
        when(loans.findByCopyIdOrderByLoanDateDescIdDesc(eq(10L), any())).thenReturn(new PageImpl<>(List.of(current, old)));
        when(members.refs(List.of(MEMBER))).thenReturn(Map.of(MEMBER, MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE)));

        List<CopyLoanHistoryItem> history = reads.copyHistory("00000010", PageRequest.of(0, 20)).getContent();

        assertThat(history.get(0).borrower().memberNumber()).isEqualTo("A000001");
        assertThat(history.get(1).borrower()).isNull();
        assertThatThrownBy(() -> reads.copyHistory("00000099", PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.COPY_NOT_FOUND));
    }

    @Test
    void exemplaireLibereVaALaPremiereReservationEnAttente() {
        Reservation first = CirculationFixtures.pending(10L, 3L, TestFixtures.NOW);
        when(reservations.lockQueueHead(eq(CirculationFixtures.BOOK_ID), any())).thenReturn(List.of(first));

        CopyRelease release = allocator.allocate(CirculationFixtures.copy(20L, CopyStatus.ON_LOAN), 2L);

        assertThat(release.destination()).isEqualTo(Destination.HOLD);
        assertThat(release.hold().pickupDeadline()).isEqualTo(TODAY.plusDays(3));
        assertThat(first.getStatus()).isEqualTo(ReservationStatus.READY);
        verify(copies).changeStatus(20L, CopyStatus.RESERVED);
        verify(audit).log(eq(2L), eq(AuditAction.STATE_CHANGE), eq(AuditObjectType.RESERVATION), eq(10L), anyMap());
    }

    @Test
    void sansFileLExemplaireRetourneEnRayonEtLesAjoutsSontProposesALaFile() {
        when(reservations.lockQueueHead(eq(CirculationFixtures.BOOK_ID), any())).thenReturn(List.of());
        when(copies.lockById(30L)).thenReturn(CirculationFixtures.copy(30L, CopyStatus.AVAILABLE));
        when(copies.lockById(31L)).thenReturn(CirculationFixtures.copy(31L, CopyStatus.RESERVED));

        CopyRelease release = allocator.allocate(CirculationFixtures.copy(20L, CopyStatus.ON_LOAN), null);
        allocator.copiesAdded(CirculationFixtures.BOOK_ID, List.of(30L, 31L), 2L);

        assertThat(release.destination()).isEqualTo(Destination.SHELF);
        verify(copies).changeStatus(20L, CopyStatus.AVAILABLE);
        verify(copies).changeStatus(30L, CopyStatus.AVAILABLE);
        verify(copies, org.mockito.Mockito.never()).changeStatus(eq(31L), any());
    }

    @Test
    void traitementsDesRetardsEtDeLAnonymisation() {
        Loan due = CirculationFixtures.loan(1L, 10L, MEMBER, TODAY.minusDays(22), TODAY.minusDays(1));
        Loan returned = CirculationFixtures.loan(2L, 11L, MEMBER, TODAY.minusMonths(6), TODAY.minusMonths(5));
        returned.giveBack(TODAY.minusMonths(5), 2L);
        when(loans.findActiveDueBefore(eq(TODAY), any())).thenReturn(List.of(due));
        when(loans.findToAnonymize(eq(TODAY.minusMonths(4)), any())).thenReturn(List.of(returned));
        LoanMaintenanceService maintenance = new LoanMaintenanceService(loans, audit, TestSettings.time(),
                TestFixtures.properties());

        assertThat(maintenance.markNextOverdueBatch()).isEqualTo(1);
        assertThat(maintenance.anonymizeNextBatch()).isEqualTo(1);

        assertThat(due.getStatus()).isEqualTo(LoanStatus.OVERDUE);
        assertThat(returned.getMemberId()).isNull();
        verify(audit).log(isNull(), eq(AuditAction.STATE_CHANGE), eq(AuditObjectType.LOAN), eq(1L), anyMap());
    }

    @Test
    void reservationNonPreteNeSeMetPasADispositionDeuxFois() {
        Reservation ready = CirculationFixtures.ready(10L, MEMBER, 20L, TODAY.plusDays(2));

        assertThatThrownBy(() -> ready.makeReady(21L, TestFixtures.NOW, TODAY)).isInstanceOf(IllegalStateException.class);
        assertThat(ready.isPickupOverdue(TODAY)).isFalse();
        assertThat(ready.getStatus()).isEqualTo(ReservationStatus.READY);
    }
}
