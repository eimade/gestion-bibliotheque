package com.bibliotheque.circulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.service.CopyCirculationService;
import com.bibliotheque.circulation.CirculationFixtures;
import com.bibliotheque.circulation.domain.Fine;
import com.bibliotheque.circulation.domain.FineStatus;
import com.bibliotheque.circulation.domain.Payment;
import com.bibliotheque.circulation.domain.PaymentMethod;
import com.bibliotheque.circulation.repository.FineRepository;
import com.bibliotheque.circulation.repository.FineRepository.Debtor;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.circulation.repository.PaymentRepository;
import com.bibliotheque.circulation.service.CirculationViews.DebtorList;
import com.bibliotheque.circulation.service.CirculationViews.MemberFines;
import com.bibliotheque.circulation.service.CirculationViews.PaymentResult;
import com.bibliotheque.circulation.service.FineService.DebtorSort;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.member.MemberFixtures;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberLookup;
import com.bibliotheque.support.TestSettings;

class FineServiceTest {

    private static final long MEMBER = 1L;
    private static final long AGENT = 2L;

    private final FineRepository fines = mock(FineRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final LoanRepository loans = mock(LoanRepository.class);
    private final CopyCirculationService copies = mock(CopyCirculationService.class);
    private final MemberLookup members = mock(MemberLookup.class);
    private final EligibilityService eligibility = mock(EligibilityService.class);
    private final CirculationAudit audit = mock(CirculationAudit.class);
    private FineService service;
    private Fine late;
    private Fine lost;

    @BeforeEach
    void setUp() {
        service = new FineService(fines, payments, loans, copies, members, eligibility,
                new FineCalculator(TestSettings.defaults()), audit, TestSettings.time());
        late = CirculationFixtures.lateFine(11L, MEMBER, 100L, "1.00");
        lost = CirculationFixtures.lateFine(12L, MEMBER, 101L, "20.00");
        when(members.require("A000001")).thenReturn(MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE));
        when(members.require(MEMBER)).thenReturn(MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE));
        when(fines.findAllById(Set.of(11L, 12L))).thenReturn(List.of(late, lost));
        when(fines.unpaidTotal(MEMBER)).thenReturn(BigDecimal.ZERO);
        when(eligibility.evaluate(anyLong(), any(), anyBoolean())).thenReturn(MemberFixtures.eligible());
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 500L);
            return saved;
        });
    }

    @Test
    void encaisseLaSelectionPourSonTotalExact() {
        PaymentResult result = service.collect("A000001", List.of(11L, 12L), PaymentMethod.CARD,
                new BigDecimal("21.00"), AGENT);

        assertThat(result.amount()).isEqualTo(new BigDecimal("21.00"));
        assertThat(result.paidFineIds()).containsExactly(11L, 12L);
        assertThat(result.unpaidTotal()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.eligible()).isTrue();
        assertThat(late.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(lost.getStatus()).isEqualTo(FineStatus.PAID);
    }

    @Test
    void refuseLePaiementPartielOuUnMontantDifferent() {
        assertThatThrownBy(() -> service.collect("A000001", List.of(11L, 12L), PaymentMethod.CASH,
                new BigDecimal("20.00"), AGENT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.PARTIAL_PAYMENT_NOT_ALLOWED);
                    assertThat(e.getMessage()).isEqualTo("Le paiement partiel n'est pas autorisé.");
                });
        assertThatThrownBy(() -> service.collect("A000001", List.of(11L, 12L), PaymentMethod.CASH,
                new BigDecimal("25.00"), AGENT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
                    assertThat(e.getMessage()).isEqualTo(
                            "Le montant encaissé doit être égal au total sélectionné (21,00 €).");
                });
        verify(payments, never()).save(any());
    }

    @Test
    void refuseUnePenaliteDejaRegleeOuDUnAutreAdherent() {
        late.pay(400L, TestSettings.time().now(), AGENT);

        assertThatThrownBy(() -> service.collect("A000001", List.of(11L, 12L), PaymentMethod.CASH,
                new BigDecimal("21.00"), AGENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FINE_ALREADY_SETTLED));
        when(members.require("A000002")).thenReturn(MemberFixtures.ref(2L, MemberStatus.ACTIVE));
        assertThatThrownBy(() -> service.collect("A000002", List.of(11L, 12L), PaymentMethod.CASH,
                new BigDecimal("21.00"), AGENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FINE_NOT_FOUND));
    }

    @Test
    void doubleEncaissementSimultaneRefuse() {
        doThrow(new ObjectOptimisticLockingFailureException(Fine.class, 11L)).when(fines).flush();

        assertThatThrownBy(() -> service.collect("A000001", List.of(11L, 12L), PaymentMethod.CASH,
                new BigDecimal("21.00"), AGENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FINE_ALREADY_SETTLED));
    }

    @Test
    void remiseGracieuseMotivee() {
        when(fines.findById(12L)).thenReturn(Optional.of(lost));

        var result = service.waive(12L, " Exemplaire retrouvé en bon état ", 1L);

        assertThat(result.status()).isEqualTo(FineStatus.WAIVED);
        assertThatThrownBy(() -> service.waive(12L, "Encore", 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FINE_ALREADY_SETTLED));
        assertThatThrownBy(() -> service.waive(99L, "Motif", 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FINE_NOT_FOUND));
    }

    @Test
    void penalitesImpayeesAvecTitreEtTotal() {
        when(fines.findByMemberIdAndStatusOrderByCreatedAtAscIdAsc(MEMBER, FineStatus.UNPAID))
                .thenReturn(List.of(late, lost));
        when(loans.findAllById(List.of(100L, 101L))).thenReturn(List.of(
                CirculationFixtures.loan(100L, 10L, MEMBER, MemberFixtures.TODAY.minusDays(30),
                        MemberFixtures.TODAY.minusDays(9)),
                CirculationFixtures.loan(101L, 11L, MEMBER, MemberFixtures.TODAY.minusDays(30),
                        MemberFixtures.TODAY.minusDays(9))));
        when(copies.describe(any())).thenReturn(Map.of(10L, CirculationFixtures.copy(10L, CopyStatus.AVAILABLE),
                11L, CirculationFixtures.copy(11L, CopyStatus.LOST)));

        MemberFines result = service.unpaid(MEMBER);

        assertThat(result.total()).isEqualTo(new BigDecimal("21.00"));
        assertThat(result.fines()).extracting(view -> view.title()).containsOnly("L'Étranger");
        assertThat(result.fines().get(0).createdOn()).isEqualTo(MemberFixtures.TODAY);
    }

    @Test
    void debiteursAvecBlocageAuDelaDuSeuil() {
        when(fines.debtorsByTotal(any())).thenReturn(new PageImpl<>(List.of(debtor(MEMBER, 2, "20.80"),
                debtor(2L, 1, "0.40"))));
        when(fines.debtorsByCount(any())).thenReturn(new PageImpl<>(List.of()));
        when(fines.debtorsByName(any())).thenReturn(new PageImpl<>(List.of()));
        when(members.refs(List.of(MEMBER, 2L))).thenReturn(Map.of(MEMBER, MemberFixtures.ref(MEMBER, MemberStatus.ACTIVE),
                2L, MemberFixtures.ref(2L, MemberStatus.ACTIVE)));
        when(fines.countDebtors()).thenReturn(2L);
        when(fines.totalUnpaid()).thenReturn(new BigDecimal("21.2"));

        DebtorList list = service.debtors(DebtorSort.TOTAL, 0, 20);

        assertThat(list.memberCount()).isEqualTo(2L);
        assertThat(list.totalAmount()).isEqualTo(new BigDecimal("21.20"));
        assertThat(list.page().content()).extracting(row -> row.blocked()).containsExactly(true, false);
        assertThat(service.debtors(DebtorSort.COUNT, 0, 20).page().content()).isEmpty();
        assertThat(service.debtors(DebtorSort.NAME, 0, 20).page().content()).isEmpty();
    }

    private static Debtor debtor(long memberId, long count, String total) {
        return new Debtor() {
            @Override
            public Long getMemberId() {
                return memberId;
            }

            @Override
            public long getFineCount() {
                return count;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }
}
