package com.bibliotheque.circulation.service;

import static com.bibliotheque.member.MemberFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bibliotheque.circulation.CirculationFixtures;
import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.LoanStatus;
import com.bibliotheque.circulation.repository.FineRepository;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.circulation.repository.MemberAmount;
import com.bibliotheque.circulation.repository.MemberCount;
import com.bibliotheque.circulation.repository.ReservationRepository;
import com.bibliotheque.circulation.service.CirculationViews.RenewalView;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.member.MemberFixtures;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberCirculation.Eligibility;
import com.bibliotheque.member.service.MemberCirculation.Obligations;
import com.bibliotheque.support.TestSettings;

/** RG-22, RG-23, RG-25, RG-35 → RG-37 : calculs et règles sans écriture. */
class CirculationRulesTest {

    private final FineCalculator calculator = new FineCalculator(TestSettings.defaults());
    private final LoanRepository loans = mock(LoanRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final FineRepository fines = mock(FineRepository.class);
    private final EligibilityService eligibility = new EligibilityService(loans, reservations, fines, calculator,
            TestSettings.defaults(), TestSettings.time());
    private final LoanRenewalPolicy renewal = new LoanRenewalPolicy(TestSettings.defaults());

    @Test
    void penaliteDeRetardCinqJours() {
        assertThat(calculator.lateFine(5)).isEqualByComparingTo("1.00");
        assertThat(calculator.lateFine(3)).isEqualTo(new BigDecimal("0.60"));
    }

    @Test
    void plafondAtteintPuisDepasse() {
        assertThat(calculator.lateFine(50)).isEqualTo(new BigDecimal("10.00"));
        assertThat(calculator.lateFine(51)).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void aucunePenaliteSansRetardEtForfaitDePerte() {
        assertThat(calculator.lateFine(0)).isEqualTo(new BigDecimal("0.00"));
        assertThat(calculator.lostItemAmount()).isEqualTo(new BigDecimal("20.00"));
        assertThat(calculator.blockingThreshold()).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void eligibleSousLaLimite() {
        stub(1L, false, "0.00", 4);

        Eligibility result = eligibility.eligibility(1L, MemberStatus.ACTIVE);

        assertThat(result.eligible()).isTrue();
        assertThat(result.remainingLoans()).isEqualTo(1);
    }

    @Test
    void tousLesMotifsSontRenvoyesEnsemble() {
        stub(1L, true, "5.50", 5);

        Eligibility result = eligibility.eligibility(1L, MemberStatus.EXPIRED);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).containsExactly("MEMBERSHIP_EXPIRED", "OVERDUE_LOAN",
                "UNPAID_FINES_ABOVE_THRESHOLD", "LOAN_LIMIT_REACHED");
        assertThat(result.remainingLoans()).isZero();
    }

    @Test
    void penalitesExactementAuSeuilAutorisees() {
        stub(1L, false, "5.00", 0);

        assertThat(eligibility.eligibility(1L, MemberStatus.ACTIVE).eligible()).isTrue();
    }

    @Test
    void horsQuotaPourProlongerOuReserver() {
        stub(1L, false, "0.00", 5);

        assertThat(eligibility.evaluate(1L, MemberStatus.ACTIVE, false).eligible()).isTrue();
        assertThat(eligibility.evaluate(1L, MemberStatus.SUSPENDED, false).reasons())
                .containsExactly("MEMBER_SUSPENDED");
        assertThat(eligibility.evaluate(1L, MemberStatus.ARCHIVED, false).reasons())
                .containsExactly("MEMBER_ARCHIVED");
    }

    @Test
    void obligationsDUnOuPlusieursAdherents() {
        when(loans.countByMemberIdAndStatusIn(1L, Loan.ONGOING)).thenReturn(1L);
        when(reservations.countByMemberIdAndStatusIn(anyLong(), any())).thenReturn(2L);
        when(fines.unpaidTotal(1L)).thenReturn(new BigDecimal("1.4"));
        when(loans.countByMembers(any(), any())).thenReturn(List.of(count(1L, 1)));
        when(reservations.countByMembers(any(), any())).thenReturn(List.of());
        when(fines.unpaidTotals(any())).thenReturn(List.of(amount(2L, "3.00")));

        assertThat(eligibility.obligations(1L)).isEqualTo(new Obligations(1, 2, new BigDecimal("1.40")));
        Map<Long, Obligations> many = eligibility.obligations(List.of(1L, 2L));
        assertThat(many.get(1L).blockingReasons()).containsExactly("ACTIVE_LOANS");
        assertThat(many.get(2L).blockingReasons()).containsExactly("UNPAID_FINES");
        assertThat(eligibility.obligations(List.of())).isEmpty();
    }

    @Test
    void prolongationAccepteeDeVingtEtUnJours() {
        Loan loan = CirculationFixtures.loan(1L, 10L, 1L, TODAY.minusDays(10), TODAY.plusDays(11));

        RenewalView result = renewal.evaluate(loan, MemberFixtures.eligible(), false, TODAY);

        assertThat(result.allowed()).isTrue();
        assertThat(result.newDueDate()).isEqualTo(TODAY.plusDays(32));
    }

    @Test
    void prolongationRefuseeSelonLePremierMotif() {
        Loan overdue = CirculationFixtures.loan(1L, 10L, 1L, TODAY.minusDays(30), TODAY.minusDays(1));
        Loan renewed = CirculationFixtures.loan(2L, 11L, 1L, TODAY.minusDays(10), TODAY.plusDays(11));
        renewed.renew(TODAY.plusDays(20));
        Loan fresh = CirculationFixtures.loan(3L, 12L, 1L, TODAY.minusDays(10), TODAY.plusDays(11));
        Eligibility blocked = new Eligibility(false, List.of("MEMBERSHIP_EXPIRED"), 1, 5, 4, BigDecimal.ZERO,
                new BigDecimal("5.00"));

        assertThat(renewal.evaluate(overdue, MemberFixtures.eligible(), false, TODAY).blockedBy())
                .isEqualTo("LOAN_OVERDUE");
        assertThat(renewal.evaluate(renewed, MemberFixtures.eligible(), false, TODAY).blockedBy())
                .isEqualTo("RENEWAL_LIMIT_REACHED");
        assertThat(renewal.evaluate(fresh, MemberFixtures.eligible(), true, TODAY).blockedBy())
                .isEqualTo("RENEWAL_BLOCKED_BY_RESERVATION");
        RenewalView notEligible = renewal.evaluate(fresh, blocked, false, TODAY);
        assertThat(notEligible.blockedBy()).isEqualTo("MEMBER_NOT_ELIGIBLE");
        assertThat(notEligible.memberReasons()).containsExactly("MEMBERSHIP_EXPIRED");
    }

    @Test
    void cycleDeVieDUnEmprunt() {
        Loan loan = CirculationFixtures.loan(1L, 10L, 1L, TODAY.minusDays(25), TODAY.minusDays(4));

        assertThat(loan.effectiveStatus(TODAY)).isEqualTo(LoanStatus.OVERDUE);
        assertThat(loan.overdueDays(TODAY)).isEqualTo(4);
        loan.markOverdue();
        loan.giveBack(TODAY, 2L);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThatThrownBy(() -> loan.declareLost(TODAY))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LOAN_NOT_ACTIVE));
        loan.unlinkMember(TestSettings.time().now());
        assertThat(loan.getMemberId()).isNull();
    }

    private void stub(long memberId, boolean overdue, String unpaid, long active) {
        when(loans.hasOverdueLoan(memberId, TODAY)).thenReturn(overdue);
        when(fines.unpaidTotal(memberId)).thenReturn(new BigDecimal(unpaid));
        when(loans.countByMemberIdAndStatusIn(memberId, Loan.ONGOING)).thenReturn(active);
    }

    private static MemberCount count(long memberId, long total) {
        return new MemberCount() {
            @Override
            public Long getMemberId() {
                return memberId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private static MemberAmount amount(long memberId, String total) {
        return new MemberAmount() {
            @Override
            public Long getMemberId() {
                return memberId;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }
}
