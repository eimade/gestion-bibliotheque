package com.bibliotheque.member.service;

import static com.bibliotheque.member.MemberFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.bibliotheque.member.domain.MemberIdentity;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.repository.MemberNumberGenerator;
import com.bibliotheque.member.repository.MemberRepository;
import com.bibliotheque.member.service.MemberCirculation.Obligations;
import com.bibliotheque.member.service.MemberViews.ArchiveCheck;
import com.bibliotheque.member.service.MemberViews.MemberDetail;
import com.bibliotheque.member.service.MemberViews.MemberSummary;
import com.bibliotheque.member.service.MemberViews.MemberToArchive;
import com.bibliotheque.support.TestFixtures;
import com.bibliotheque.support.TestSettings;

class MemberServiceTest {

    private static final long ACTOR = 2L;

    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberNumberGenerator numbers = mock(MemberNumberGenerator.class);
    private final MemberCirculation circulation = mock(MemberCirculation.class);
    private final MemberAccountService accounts = mock(MemberAccountService.class);
    private final AuditService audit = mock(AuditService.class);
    private MemberService service;

    @BeforeEach
    void setUp() {
        service = new MemberService(members, numbers, new MembershipPolicy(TestSettings.defaults()), circulation,
                accounts, audit, TestSettings.time(), TestFixtures.properties());
        when(numbers.next()).thenReturn("A000124");
        when(members.save(any(Member.class))).thenAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 124L);
            ReflectionTestUtils.setField(saved, "version", 0);
            return saved;
        });
        when(circulation.eligibility(anyLong(), any())).thenReturn(MemberFixtures.eligible());
        when(circulation.obligations(anyLong())).thenReturn(Obligations.NONE);
        when(accounts.findByMember(anyLong())).thenAnswer(invocation -> Optional.of(
                new MemberAccount(50L, invocation.getArgument(0), "jean.martin@exemple.fr", AccountStatus.ACTIVE)));
    }

    @Test
    void inscritUnAdulteActifPourDouzeMoisAvecSonCompte() {
        MemberDetail detail = service.register(MemberFixtures.command(MemberFixtures.adult()), "MotDePasse2026",
                false, ACTOR);

        assertThat(detail.memberNumber()).isEqualTo("A000124");
        assertThat(detail.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(detail.expiryDate()).isEqualTo(LocalDate.of(2027, 10, 17));
        assertThat(detail.email()).isEqualTo("jean.martin@exemple.fr");
        verify(accounts).create(124L, "Jean.Martin@exemple.fr", "MotDePasse2026", ACTOR);
        verify(audit).recordAs(eq(ACTOR), eq(AuditAction.CREATE), eq(AuditObjectType.MEMBER), eq("A000124"), anyMap());
    }

    @Test
    void refuseUnMineurSansRepresentantLegal() {
        MemberIdentity child = new MemberIdentity("Petit", "Léa", TODAY.minusYears(10), null);

        assertThatThrownBy(() -> service.register(MemberFixtures.command(child), "MotDePasse2026", false, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.GUARDIAN_REQUIRED));
        verify(members, never()).save(any());
    }

    @Test
    void accepteUnMineurAvecRepresentantLegal() {
        MemberIdentity child = new MemberIdentity("Petit", "Léa", TODAY.minusYears(10), MemberFixtures.guardian());

        assertThat(service.register(MemberFixtures.command(child), "MotDePasse2026", false, ACTOR).guardian())
                .isNotNull();
    }

    @Test
    void refuseUneDateDeNaissanceFuture() {
        MemberIdentity future = new MemberIdentity("Martin", "Jean", TODAY, null);

        assertThatThrownBy(() -> service.register(MemberFixtures.command(future), "MotDePasse2026", false, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void refuseUnEmailDejaUtilise() {
        when(accounts.isEmailUsed("Jean.Martin@exemple.fr")).thenReturn(true);

        assertThatThrownBy(() -> service.register(MemberFixtures.command(MemberFixtures.adult()), "MotDePasse2026",
                false, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_USED));
    }

    @Test
    void signaleUnHomonymeSaufConfirmation() {
        Member existing = MemberFixtures.member(123L, "A000123", TODAY.plusMonths(6), MemberStatus.ACTIVE);
        when(members.findFirstByLastNameAndFirstNameAndBirthDateAndStatusNot("Martin", "Jean",
                LocalDate.of(1990, 3, 12), MemberStatus.ARCHIVED)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(MemberFixtures.command(MemberFixtures.adult()), "MotDePasse2026",
                false, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_MEMBER_SUSPECTED);
                    assertThat(e.properties()).containsKey("existingMember");
                });
        assertThat(service.register(MemberFixtures.command(MemberFixtures.adult()), "MotDePasse2026", true, ACTOR)
                .memberNumber()).isEqualTo("A000124");
    }

    @Test
    void rechercheParNumeroExactRetrouveAussiUnArchive() {
        Member archived = MemberFixtures.member(9L, "A000009", TODAY.minusYears(4), MemberStatus.ARCHIVED);
        when(members.findByMemberNumber("A000009")).thenReturn(Optional.of(archived));

        var page = service.search("a000009", null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(MemberSummary::memberNumber).containsExactly("A000009");
    }

    @Test
    void rechercheParNomExclutLesArchives() {
        Member active = MemberFixtures.member(1L, "A000001", TODAY.plusMonths(3), MemberStatus.ACTIVE);
        when(members.search(any(), eq("%mart%"), any())).thenReturn(new PageImpl<>(List.of(active)));

        var page = service.search("mart", null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        verify(members).search(eq(EnumSet.of(MemberStatus.ACTIVE, MemberStatus.EXPIRED,
                MemberStatus.SUSPENDED)), eq("%mart%"), any());
    }

    @Test
    void ficheIntrouvable() {
        assertThatThrownBy(() -> service.get("A000999"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Aucun adhérent ne porte le numéro A000999.");
                });
    }

    @Test
    void modifieEtJournaliseLesValeursAvantApres() {
        Member member = MemberFixtures.member(1L, "A000001", TODAY.plusMonths(3), MemberStatus.ACTIVE);
        when(members.findByMemberNumber("A000001")).thenReturn(Optional.of(member));
        MemberCommand command = new MemberCommand(MemberFixtures.adult(),
                new MemberContact("0612345678", "3 rue Neuve", "69001", "Lyon"),
                "nouveau@exemple.fr");

        MemberDetail detail = service.update("A000001", command, 0, ACTOR);

        assertThat(detail.city()).isEqualTo("Lyon");
        verify(accounts).changeEmail(1L, "nouveau@exemple.fr", ACTOR);
        verify(audit).recordAs(eq(ACTOR), eq(AuditAction.UPDATE), eq(AuditObjectType.MEMBER), eq("A000001"),
                ArgumentMatchers.<Map<String, ?>>argThat(changes -> changes.containsKey("city")
                        && changes.containsKey("street") && !changes.containsKey("lastName")));
    }

    @Test
    void refuseDeModifierUnArchiveOuUneVersionPerimee() {
        Member archived = MemberFixtures.member(9L, "A000009", TODAY.minusYears(4), MemberStatus.ARCHIVED);
        Member active = MemberFixtures.member(1L, "A000001", TODAY.plusMonths(3), MemberStatus.ACTIVE);
        when(members.findByMemberNumber("A000009")).thenReturn(Optional.of(archived));
        when(members.findByMemberNumber("A000001")).thenReturn(Optional.of(active));
        MemberCommand command = MemberFixtures.command(MemberFixtures.adult());

        assertThatThrownBy(() -> service.update("A000009", command, 0, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_ARCHIVED));
        assertThatThrownBy(() -> service.update("A000001", command, 3, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    void renouvelleUnAdherentExpire() {
        Member member = MemberFixtures.member(1L, "A000001", LocalDate.of(2026, 9, 1), MemberStatus.EXPIRED);
        when(members.findByMemberNumber("A000001")).thenReturn(Optional.of(member));

        MemberDetail detail = service.renew("A000001", ACTOR);

        assertThat(detail.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(detail.expiryDate()).isEqualTo(LocalDate.of(2027, 10, 17));
    }

    @Test
    void refuseUnRenouvellementTropTotOuSuspendu() {
        Member early = MemberFixtures.member(1L, "A000001", TODAY.plusMonths(6), MemberStatus.ACTIVE);
        Member suspended = MemberFixtures.member(2L, "A000002", TODAY.plusDays(10), MemberStatus.SUSPENDED);
        Member archived = MemberFixtures.member(3L, "A000003", TODAY.minusYears(4), MemberStatus.ARCHIVED);
        when(members.findByMemberNumber("A000001")).thenReturn(Optional.of(early));
        when(members.findByMemberNumber("A000002")).thenReturn(Optional.of(suspended));
        when(members.findByMemberNumber("A000003")).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.renew("A000001", ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.RENEWAL_TOO_EARLY);
                    assertThat(e.getMessage()).isEqualTo("Renouvellement possible à partir du 18/03/2027.");
                    assertThat(e.properties()).containsEntry("renewableFrom", "2027-03-18");
                });
        assertThatThrownBy(() -> service.renew("A000002", ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_SUSPENDED));
        assertThatThrownBy(() -> service.renew("A000003", ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_ARCHIVED));
    }

    @Test
    void suspendPuisReactiveSelonLaDateDExpiration() {
        Member valid = MemberFixtures.member(1L, "A000001", TODAY.plusMonths(3), MemberStatus.ACTIVE);
        Member lapsed = MemberFixtures.member(2L, "A000002", TODAY.minusDays(3), MemberStatus.SUSPENDED);
        when(members.findByMemberNumber("A000001")).thenReturn(Optional.of(valid));
        when(members.findByMemberNumber("A000002")).thenReturn(Optional.of(lapsed));

        assertThat(service.suspend("A000001", " Litige sur document abîmé ", ACTOR).status())
                .isEqualTo(MemberStatus.SUSPENDED);
        assertThat(valid.getSuspensionReason()).isEqualTo("Litige sur document abîmé");
        assertThat(service.reactivate("A000001", ACTOR).status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(service.reactivate("A000002", ACTOR).status()).isEqualTo(MemberStatus.EXPIRED);
    }

    @Test
    void reactiverUnArchiveEstRefuse() {
        Member archived = MemberFixtures.member(9L, "A000009", TODAY.minusYears(4), MemberStatus.ARCHIVED);
        when(members.findByMemberNumber("A000009")).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.reactivate("A000009", ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_ARCHIVED));
    }

    @Test
    void archivageBloqueParUnEmpruntEnCours() {
        Member member = MemberFixtures.member(1L, "A000001", TODAY.minusYears(4), MemberStatus.EXPIRED);
        when(members.lockByMemberNumber("A000001")).thenReturn(Optional.of(member));
        when(circulation.obligations(1L)).thenReturn(new Obligations(1, 0, new BigDecimal("1.40")));

        assertThatThrownBy(() -> service.archive("A000001", ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.ARCHIVE_BLOCKED);
                    assertThat(e.reasons()).containsExactly("ACTIVE_LOANS", "UNPAID_FINES");
                });
        verify(accounts, never()).archive(anyLong());
    }

    @Test
    void archiveEfficeLesDonneesPersonnellesEtDesactiveLeCompte() {
        Member member = MemberFixtures.member(1L, "A000001", TODAY.minusYears(4), MemberStatus.EXPIRED);
        when(members.lockByMemberNumber("A000001")).thenReturn(Optional.of(member));

        MemberDetail detail = service.archive("A000001", ACTOR);

        assertThat(detail.status()).isEqualTo(MemberStatus.ARCHIVED);
        assertThat(detail.lastName()).isNull();
        assertThat(detail.street()).isNull();
        assertThat(detail.postalCode()).isEqualTo("75011");
        verify(accounts).archive(1L);
    }

    @Test
    void verifieLArchivabiliteEtListeLesAdherentsAArchiver() {
        Member member = MemberFixtures.member(1L, "A000001", TODAY.minusYears(4), MemberStatus.EXPIRED);
        when(members.findByMemberNumber("A000001")).thenReturn(Optional.of(member));
        when(members.findByStatusAndExpiryDateBefore(eq(MemberStatus.EXPIRED), eq(LocalDate.of(2023, 10, 17)), any()))
                .thenReturn(new PageImpl<>(List.of(member)));
        when(circulation.obligations(List.of(1L))).thenReturn(Map.of(1L, new Obligations(0, 0,
                new BigDecimal("2.00"))));

        ArchiveCheck check = service.archiveCheck("A000001");
        List<MemberToArchive> toArchive = service.toArchive(PageRequest.of(0, 20)).getContent();

        assertThat(check.archivable()).isTrue();
        assertThat(toArchive).singleElement().satisfies(row -> {
            assertThat(row.archivable()).isFalse();
            assertThat(row.reasons()).containsExactly("UNPAID_FINES");
        });
    }

    @Test
    void unAdherentActifEchuEstPresenteExpire() {
        Member member = MemberFixtures.member(1L, "A000001", TODAY.minusDays(1), MemberStatus.ACTIVE);
        when(members.findByMemberNumber("A000001")).thenReturn(Optional.of(member));

        assertThat(service.get("A000001").status()).isEqualTo(MemberStatus.EXPIRED);
        verify(circulation).eligibility(1L, MemberStatus.EXPIRED);
        verify(accounts, never()).create(anyLong(), anyString(), anyString(), anyLong());
    }
}
