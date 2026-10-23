package com.bibliotheque.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.SessionRevoker;
import com.bibliotheque.support.TestFixtures;

class StaffAccountServiceTest {

    private static final long ADMIN_ID = 1L;

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final SessionRevoker sessionRevoker = mock(SessionRevoker.class);
    private final AuditService audit = mock(AuditService.class);
    private final AccountLabels labels = mock(AccountLabels.class);
    private StaffAccountService service;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        service = new StaffAccountService(accounts, passwords, sessionRevoker, audit, labels,
                new BusinessTime(Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC), TestFixtures.properties()));
        admin = TestFixtures.staff(ADMIN_ID, AccountRole.ADMIN, "Admin2026Secret");
        when(accounts.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(labels.of(admin)).thenReturn("LEROY Anne");
    }

    @Test
    void rechercheAvecUnMotifEchappeEtSignaleLeCompteDeLAppelant() {
        var pageable = PageRequest.of(0, 20);
        when(accounts.searchStaff(any(), eq(null), eq(AccountStatus.ACTIVE), eq("%a!_b%"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(admin), pageable, 1));

        var page = service.search("a_b", null, AccountStatus.ACTIVE, pageable, ADMIN_ID);

        assertThat(page.getContent()).singleElement().satisfies(view -> {
            assertThat(view.me()).isTrue();
            assertThat(view.role()).isEqualTo(AccountRole.ADMIN);
        });
    }

    @Test
    void refuseLeFiltreSurLeRoleAdherent() {
        assertThatThrownBy(() -> service.search(null, AccountRole.MEMBER, null, PageRequest.of(0, 20), ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void refuseDeCreerUnCompteAdherent() {
        assertThatThrownBy(() -> service.create(command(AccountRole.MEMBER, "x@exemple.fr"), ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ROLE_NOT_ALLOWED));
    }

    @Test
    void refuseUnEmailDejaUtilise() {
        when(accounts.existsByEmail("marie.dupont@exemple.fr")).thenReturn(true);

        assertThatThrownBy(() -> service.create(command(AccountRole.LIBRARIAN, " Marie.Dupont@Exemple.fr"), ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_USED));
    }

    @Test
    void creeUnCompteActifAvecMotDePasseTemporaire() {
        when(passwords.hashNewPassword("Initial2026Secret", "initialPassword")).thenReturn("{bcrypt}hash");
        when(accounts.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        StaffAccountView view = service.create(command(AccountRole.LIBRARIAN, "Marie.Dupont@Exemple.fr"), ADMIN_ID);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("marie.dupont@exemple.fr");
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getValue().isPasswordTemporary()).isTrue();
        assertThat(view.id()).isEqualTo(10L);
        assertThat(view.me()).isFalse();
        verify(passwords).recordCurrentInHistory(saved.getValue());
        verify(audit).record(eq(ADMIN_ID), eq("LEROY Anne"), eq(AuditAction.CREATE), eq(AuditObjectType.ACCOUNT),
                eq("10"), anyMap());
    }

    @Test
    void refuseLAutoDesactivation() {
        assertThatThrownBy(() -> service.deactivate(ADMIN_ID, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SELF_DEACTIVATION_FORBIDDEN));
    }

    @Test
    void refuseDeDesactiverLeDernierAdministrateur() {
        UserAccount otherAdmin = TestFixtures.staff(2L, AccountRole.ADMIN, "Admin2026Secret");
        when(accounts.findById(2L)).thenReturn(Optional.of(otherAdmin));
        when(accounts.lockByRoleAndStatusNot(AccountRole.ADMIN, AccountStatus.DISABLED)).thenReturn(List.of(otherAdmin));

        assertThatThrownBy(() -> service.deactivate(2L, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.LAST_ADMIN_REQUIRED));
        verify(sessionRevoker, never()).revokeAll(2L);
    }

    @Test
    void desactiveUnAdministrateurQuandUnAutreResteActif() {
        UserAccount otherAdmin = TestFixtures.staff(2L, AccountRole.ADMIN, "Admin2026Secret");
        when(accounts.findById(2L)).thenReturn(Optional.of(otherAdmin));
        when(accounts.lockByRoleAndStatusNot(AccountRole.ADMIN, AccountStatus.DISABLED))
                .thenReturn(List.of(admin, otherAdmin));

        assertThat(service.deactivate(2L, ADMIN_ID).status()).isEqualTo(AccountStatus.DISABLED);
    }

    @Test
    void desactiveUnBibliothecaireEtSupprimeSesSessions() {
        UserAccount librarian = TestFixtures.staff(5L, AccountRole.LIBRARIAN, "Agent2026Secret");
        when(accounts.findById(5L)).thenReturn(Optional.of(librarian));

        StaffAccountView view = service.deactivate(5L, ADMIN_ID);

        assertThat(view.status()).isEqualTo(AccountStatus.DISABLED);
        verify(sessionRevoker).revokeAll(5L);
        verify(accounts).flush();
        verify(audit).record(eq(ADMIN_ID), eq("LEROY Anne"), eq(AuditAction.STATE_CHANGE),
                eq(AuditObjectType.ACCOUNT), eq("5"), anyMap());
    }

    @Test
    void laDesactivationDUnCompteDejaDesactiveNeFaitRien() {
        UserAccount librarian = TestFixtures.staff(5L, AccountRole.LIBRARIAN, "Agent2026Secret");
        librarian.disable();
        when(accounts.findById(5L)).thenReturn(Optional.of(librarian));

        service.deactivate(5L, ADMIN_ID);

        verify(sessionRevoker, never()).revokeAll(5L);
    }

    @Test
    void neTraitePasUnCompteAdherentCommeUnCompteDuPersonnel() {
        when(accounts.findById(8L)).thenReturn(Optional.of(TestFixtures.member(8L, 80L, "Membre2026Secret")));

        assertThatThrownBy(() -> service.get(8L, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Test
    void reactiveAvecUnCompteurDEchecsAZero() {
        UserAccount librarian = TestFixtures.staff(5L, AccountRole.LIBRARIAN, "Agent2026Secret");
        librarian.disable();
        when(accounts.findById(5L)).thenReturn(Optional.of(librarian));

        StaffAccountView view = service.reactivate(5L, ADMIN_ID);

        assertThat(view.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(librarian.getFailedLoginCount()).isZero();
    }

    private static CreateStaffAccountCommand command(AccountRole role, String email) {
        return new CreateStaffAccountCommand("Dupont", "Marie", email, role, "Initial2026Secret");
    }
}
