package com.bibliotheque.member.service;

import static com.bibliotheque.member.MemberFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.service.MemberAccountService;
import com.bibliotheque.auth.service.MemberAccountService.MemberAccount;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.member.MemberFixtures;
import com.bibliotheque.member.domain.Member;
import com.bibliotheque.member.domain.MemberContact;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.repository.MemberRepository;
import com.bibliotheque.member.service.MemberViews.MemberRef;
import com.bibliotheque.member.service.MemberViews.MyMember;
import com.bibliotheque.support.TestSettings;

class MemberSpaceAndLookupTest {

    private static final long ACCOUNT = 50L;

    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberAccountService accounts = mock(MemberAccountService.class);
    private final AuditService audit = mock(AuditService.class);
    private MemberLookup lookup;
    private MemberSpaceService space;
    private Member member;

    @BeforeEach
    void setUp() {
        lookup = new MemberLookup(members, accounts, TestSettings.time());
        space = new MemberSpaceService(members, lookup, accounts, audit, TestSettings.time());
        member = MemberFixtures.member(1L, "A000001", TODAY.plusMonths(3), MemberStatus.ACTIVE);
        when(members.findById(1L)).thenReturn(Optional.of(member));
        when(accounts.memberIdOf(ACCOUNT)).thenReturn(Optional.of(1L));
        when(accounts.findByMember(1L)).thenReturn(Optional.of(
                new MemberAccount(ACCOUNT, 1L, "jean.martin@exemple.fr", AccountStatus.ACTIVE)));
    }

    @Test
    void lAdherentVoitSesPropresDonnees() {
        MyMember me = space.me(ACCOUNT);

        assertThat(me.memberNumber()).isEqualTo("A000001");
        assertThat(me.email()).isEqualTo("jean.martin@exemple.fr");
    }

    @Test
    void unCompteSansAdherentEstRefuse() {
        when(accounts.memberIdOf(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> space.me(7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void modifieSesCoordonneesEtJournaliseSeulementLesChangements() {
        MyMember me = space.updateContact(ACCOUNT, new MemberContact("0698765432", "12 rue des Lilas", "75011", "Paris"),
                0);

        assertThat(me.phone()).isEqualTo("0698765432");
        verify(audit).recordAs(eq(ACCOUNT), eq(AuditAction.UPDATE), eq(AuditObjectType.MEMBER), eq("A000001"),
                eq(Map.of("phone", Map.of("before", "0612345678", "after", "0698765432"))));
    }

    @Test
    void aucuneEcritureDAuditSansChangement() {
        space.updateContact(ACCOUNT, MemberFixtures.contact(), 0);

        verify(audit, never()).recordAs(any(Long.class), any(), any(), any(), anyMap());
    }

    @Test
    void lookupFournitLeStatutEffectifEtLesContacts() {
        Member lapsed = MemberFixtures.member(2L, "A000002", TODAY.minusDays(2), MemberStatus.ACTIVE);
        when(members.findByMemberNumber("A000002")).thenReturn(Optional.of(lapsed));
        when(members.lockByMemberNumber("A000001")).thenReturn(Optional.of(member));
        when(members.lockById(1L)).thenReturn(Optional.of(member));
        when(members.findAllById(List.of(1L, 2L))).thenReturn(List.of(member, lapsed));
        when(accounts.findByMembers(List.of(1L, 2L))).thenReturn(Map.of(1L,
                new MemberAccount(ACCOUNT, 1L, "jean.martin@exemple.fr", AccountStatus.ACTIVE)));

        assertThat(lookup.require("A000002").status()).isEqualTo(MemberStatus.EXPIRED);
        assertThat(lookup.lock("A000001").email()).isEqualTo("jean.martin@exemple.fr");
        assertThat(lookup.lock(1L).id()).isEqualTo(1L);
        assertThat(lookup.require(1L).memberNumber()).isEqualTo("A000001");
        Map<Long, MemberRef> refs = lookup.refs(List.of(1L, 2L));
        assertThat(refs.get(2L).email()).isNull();
        assertThat(lookup.refs(List.of())).isEmpty();
        assertThat(lookup.memberIdOfAccount(ACCOUNT)).isEqualTo(1L);
    }

    @Test
    void lookupNumeroInconnu() {
        assertThatThrownBy(() -> lookup.lock("A000999"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void expireLesAdhesionsEchuesParLotEtJournaliseAvecLeSysteme() {
        Member lapsed = MemberFixtures.member(2L, "A000002", TODAY.minusDays(1), MemberStatus.ACTIVE);
        when(members.findExpiring(eq(MemberStatus.ACTIVE), eq(TODAY), any())).thenReturn(List.of(lapsed));
        MembershipExpiryService expiry = new MembershipExpiryService(members, audit, TestSettings.time());

        assertThat(expiry.expireNextBatch()).isEqualTo(1);

        assertThat(lapsed.getStatus()).isEqualTo(MemberStatus.EXPIRED);
        verify(audit, times(1)).record(isNull(), eq(AuditService.SYSTEM_LABEL), eq(AuditAction.STATE_CHANGE),
                eq(AuditObjectType.MEMBER), eq("A000002"), anyMap());
    }
}
