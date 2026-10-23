package com.bibliotheque.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.support.MutableClock;
import com.bibliotheque.support.TestFixtures;

class AuthenticationServiceTest {

    private static final String PASSWORD = "Correct2026Secret";

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final SessionUserService sessionUsers = mock(SessionUserService.class);
    private final AuditService audit = mock(AuditService.class);
    private final AccountLabels labels = mock(AccountLabels.class);
    private final MutableClock clock = new MutableClock(TestFixtures.NOW);
    private AuthenticationService service;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        service = newService(TransactionOperations.withoutTransaction());
        account = TestFixtures.staff(3L, AccountRole.LIBRARIAN, PASSWORD);
        when(accounts.findByEmail("agent3@exemple.fr")).thenReturn(Optional.of(account));
        when(labels.of(account)).thenReturn("DUPONT Marie");
        when(sessionUsers.describe(account)).thenReturn(
                new SessionUser(3L, "agent3@exemple.fr", AccountRole.LIBRARIAN, "Dupont", "Marie", true, null));
    }

    @Test
    void connecteAvecDesIdentifiantsCorrectsEtNormaliseLEmail() {
        account.registerFailedLogin(TestFixtures.NOW, 5, Duration.ofMinutes(15));

        LoginOutcome outcome = service.login("  AGENT3@Exemple.FR ", PASSWORD);

        assertThat(outcome).isInstanceOf(LoginOutcome.Authenticated.class);
        assertThat(account.getFailedLoginCount()).isZero();
        verify(audit).record(3L, "DUPONT Marie", AuditAction.LOGIN_SUCCESS, AuditObjectType.ACCOUNT, "3");
    }

    @Test
    void refuseUnEmailInconnuSansReveler() {
        LoginOutcome outcome = service.login("inconnu@exemple.fr", PASSWORD);

        assertThat(outcome).isEqualTo(new LoginOutcome.Rejected(ErrorCode.INVALID_CREDENTIALS, null));
        verify(audit).record(isNull(), eq(AuthenticationService.UNKNOWN_ACTOR), eq(AuditAction.LOGIN_FAILURE),
                eq(AuditObjectType.ACCOUNT), eq("inconnu"));
    }

    @Test
    void compteLesEchecsSansVerrouillerAvantLeCinquieme() {
        for (int i = 0; i < 4; i++) {
            assertThat(service.login("agent3@exemple.fr", "Mauvais2026x"))
                    .isEqualTo(new LoginOutcome.Rejected(ErrorCode.INVALID_CREDENTIALS, null));
        }
        assertThat(account.getFailedLoginCount()).isEqualTo(4);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void verrouilleAuCinquiemeEchecPendantQuinzeMinutes() {
        for (int i = 0; i < 4; i++) {
            service.login("agent3@exemple.fr", "Mauvais2026x");
        }

        LoginOutcome outcome = service.login("agent3@exemple.fr", "Mauvais2026x");

        assertThat(outcome).isEqualTo(new LoginOutcome.Rejected(ErrorCode.ACCOUNT_LOCKED,
                TestFixtures.NOW.plus(Duration.ofMinutes(15))));
        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
        verify(audit).record(isNull(), eq(AuditService.SYSTEM_LABEL), eq(AuditAction.STATE_CHANGE),
                eq(AuditObjectType.ACCOUNT), eq("3"), anyMap());
    }

    @Test
    void refuseLeBonMotDePassePendantLeVerrouillage() {
        lock();
        clock.advance(Duration.ofMinutes(14));

        assertThat(service.login("agent3@exemple.fr", PASSWORD))
                .isInstanceOfSatisfying(LoginOutcome.Rejected.class,
                        rejected -> assertThat(rejected.code()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));
    }

    @Test
    void leveLeVerrouEchuALaTentativeSuivante() {
        lock();
        clock.advance(Duration.ofMinutes(15));

        assertThat(service.login("agent3@exemple.fr", PASSWORD)).isInstanceOf(LoginOutcome.Authenticated.class);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getLockedUntil()).isNull();
    }

    @Test
    void refuseUnCompteDesactiveAvecLeBonMotDePasse() {
        account.disable();

        assertThat(service.login("agent3@exemple.fr", PASSWORD))
                .isEqualTo(new LoginOutcome.Rejected(ErrorCode.ACCOUNT_DISABLED, null));
    }

    @Test
    void neRevelePasLaDesactivationAvecUnMauvaisMotDePasse() {
        account.disable();

        assertThat(service.login("agent3@exemple.fr", "Mauvais2026x"))
                .isEqualTo(new LoginOutcome.Rejected(ErrorCode.INVALID_CREDENTIALS, null));
        assertThat(account.getFailedLoginCount()).isZero();
    }

    @Test
    void rejoueUneFoisUneTentativePerdanteEnConcurrence() {
        TransactionOperations failingOnce = new TransactionOperations() {
            private int calls;

            @Override
            public <T> T execute(TransactionCallback<T> action) {
                if (calls++ == 0) {
                    throw new ObjectOptimisticLockingFailureException(UserAccount.class, 3L);
                }
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };

        assertThat(newService(failingOnce).login("agent3@exemple.fr", PASSWORD))
                .isInstanceOf(LoginOutcome.Authenticated.class);
    }

    @Test
    void journaliseLaDeconnexion() {
        when(accounts.findById(3L)).thenReturn(Optional.of(account));

        service.recordLogout(3L);

        verify(audit).record(3L, "DUPONT Marie", AuditAction.LOGOUT, AuditObjectType.ACCOUNT, "3");
    }

    private void lock() {
        for (int i = 0; i < 5; i++) {
            service.login("agent3@exemple.fr", "Mauvais2026x");
        }
        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
    }

    private AuthenticationService newService(TransactionOperations transactions) {
        var properties = TestFixtures.properties();
        return new AuthenticationService(accounts, TestFixtures.ENCODER, sessionUsers, audit, labels,
                new BusinessTime(clock, properties), transactions, properties);
    }
}
