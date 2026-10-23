package com.bibliotheque.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.security.SessionRevoker;
import com.bibliotheque.support.TestFixtures;

class AccountPasswordResetServiceTest {

    private static final AccountPrincipal LIBRARIAN = new AccountPrincipal(2L, "LIBRARIAN", false);
    private static final AccountPrincipal ADMIN = new AccountPrincipal(1L, "ADMIN", false);

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final SessionRevoker sessionRevoker = mock(SessionRevoker.class);
    private final AuditService audit = mock(AuditService.class);
    private final AccountLabels labels = mock(AccountLabels.class);
    private AccountPasswordResetService service;
    private UserAccount memberAccount;

    @BeforeEach
    void setUp() {
        service = new AccountPasswordResetService(accounts, passwords, sessionRevoker, audit, labels);
        memberAccount = TestFixtures.member(9L, 90L, "Membre2026Secret");
        when(accounts.findById(9L)).thenReturn(Optional.of(memberAccount));
        UserAccount librarianAccount = TestFixtures.staff(2L, AccountRole.LIBRARIAN, "Agent2026Secret");
        when(accounts.findById(2L)).thenReturn(Optional.of(librarianAccount));
        when(labels.of(librarianAccount)).thenReturn("DUPONT Marie");
    }

    @Test
    void unBibliothecaireReinitialiseLeCompteDUnAdherentVerrouille() {
        for (int i = 0; i < 5; i++) {
            memberAccount.registerFailedLogin(TestFixtures.NOW, 5, Duration.ofMinutes(15));
        }

        service.reset(9L, "Temporaire2026x", LIBRARIAN);

        verify(passwords).assignTemporaryPassword(memberAccount, "Temporaire2026x", "newPassword");
        assertThat(memberAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(memberAccount.getFailedLoginCount()).isZero();
        verify(sessionRevoker).revokeAll(9L);
        verify(audit).record(eq(2L), eq("DUPONT Marie"), eq(AuditAction.UPDATE), eq(AuditObjectType.ACCOUNT),
                eq("9"), anyMap());
    }

    @Test
    void unBibliothecaireNePeutPasReinitialiserUnCompteDuPersonnel() {
        when(accounts.findById(3L)).thenReturn(Optional.of(TestFixtures.staff(3L, AccountRole.ADMIN, "Admin2026Secret")));

        assertThatThrownBy(() -> service.reset(3L, "Temporaire2026x", LIBRARIAN))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.ACCESS_DENIED);
                    assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        verifyNoInteractions(passwords, sessionRevoker);
    }

    @Test
    void refuseUnCompteDesactiveAvecUnConflit() {
        memberAccount.disable();

        assertThatThrownBy(() -> service.reset(9L, "Temporaire2026x", ADMIN))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.ACCOUNT_DISABLED);
                    assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void refuseUnCompteInexistant() {
        assertThatThrownBy(() -> service.reset(99L, "Temporaire2026x", ADMIN))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
    }
}
