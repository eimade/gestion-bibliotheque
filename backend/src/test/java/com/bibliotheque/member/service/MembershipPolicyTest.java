package com.bibliotheque.member.service;

import static com.bibliotheque.member.MemberFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.bibliotheque.member.MemberFixtures;
import com.bibliotheque.member.domain.Member;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MembershipPolicy.Renewal;
import com.bibliotheque.support.TestSettings;

class MembershipPolicyTest {

    private final MembershipPolicy policy = new MembershipPolicy(TestSettings.defaults());

    @Test
    void inscriptionValableDouzeMois() {
        assertThat(policy.initialExpiry(TODAY)).isEqualTo(LocalDate.of(2027, 10, 17));
    }

    @Test
    void renouvellementAnticipePartDeLEcheanceActuelle() {
        Member member = MemberFixtures.member(1L, "A000001", LocalDate.of(2026, 11, 1), MemberStatus.ACTIVE);

        Renewal renewal = policy.renewal(member, TODAY);

        assertThat(renewal.allowed()).isTrue();
        assertThat(renewal.newExpiryDate()).isEqualTo(LocalDate.of(2027, 11, 1));
    }

    @Test
    void renouvellementApresExpirationPartDuJour() {
        Member member = MemberFixtures.member(1L, "A000001", LocalDate.of(2026, 9, 1), MemberStatus.EXPIRED);

        assertThat(policy.renewal(member, TODAY).newExpiryDate()).isEqualTo(LocalDate.of(2027, 10, 17));
    }

    @Test
    void renouvellementTropTotIndiqueLaDatePossible() {
        Member member = MemberFixtures.member(1L, "A000001", LocalDate.of(2027, 1, 15), MemberStatus.ACTIVE);

        Renewal renewal = policy.renewal(member, TODAY);

        assertThat(renewal.allowed()).isFalse();
        assertThat(renewal.blockedBy()).isEqualTo("RENEWAL_TOO_EARLY");
        assertThat(renewal.renewableFrom()).isEqualTo(LocalDate.of(2026, 12, 16));
    }

    @Test
    void renouvellementPossibleExactementTrenteJoursAvant() {
        Member member = MemberFixtures.member(1L, "A000001", TODAY.plusDays(30), MemberStatus.ACTIVE);

        assertThat(policy.renewal(member, TODAY).allowed()).isTrue();
    }

    @Test
    void suspenduOuArchiveNeSeRenouvellePas() {
        Member suspended = MemberFixtures.member(1L, "A000001", TODAY.plusDays(5), MemberStatus.SUSPENDED);
        Member archived = MemberFixtures.member(2L, "A000002", TODAY.minusYears(4), MemberStatus.ARCHIVED);

        assertThat(policy.renewal(suspended, TODAY).blockedBy()).isEqualTo("MEMBER_SUSPENDED");
        assertThat(policy.renewal(archived, TODAY).blockedBy()).isEqualTo("MEMBER_ARCHIVED");
    }

    @Test
    void representantLegalExigeSousQuinzeAnsMaisPasLeJourAnniversaire() {
        assertThat(policy.requiresGuardian(TODAY.minusYears(10), TODAY)).isTrue();
        assertThat(policy.requiresGuardian(TODAY.minusYears(15).plusDays(1), TODAY)).isTrue();
        assertThat(policy.requiresGuardian(TODAY.minusYears(15), TODAY)).isFalse();
    }

    @Test
    void alerteQuandLAdhesionExpireDansTrenteJoursAuPlus() {
        assertThat(policy.expiresSoon(MemberFixtures.member(1L, "A000001", TODAY.plusDays(30), MemberStatus.ACTIVE),
                TODAY)).isTrue();
        assertThat(policy.expiresSoon(MemberFixtures.member(1L, "A000001", TODAY.plusDays(31), MemberStatus.ACTIVE),
                TODAY)).isFalse();
        assertThat(policy.expiresSoon(MemberFixtures.member(1L, "A000001", TODAY.minusDays(1), MemberStatus.ACTIVE),
                TODAY)).isFalse();
    }
}
