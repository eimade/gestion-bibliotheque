package com.bibliotheque.auth.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.AppProperties;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.ErrorCode;

/**
 * Connexion (US-001) avec verrouillage après échecs consécutifs (RG-02). Le message ne révèle pas
 * l'existence d'un e-mail ; un e-mail inconnu coûte le même calcul BCrypt qu'un compte réel.
 */
@Service
public class AuthenticationService {

    static final String UNKNOWN_ACTOR = "Inconnu";

    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final SessionUserService sessionUsers;
    private final AuditService audit;
    private final AccountLabels labels;
    private final BusinessTime time;
    private final TransactionOperations transactions;
    private final AppProperties.Security.Login loginRules;
    private final String dummyHash;

    public AuthenticationService(UserAccountRepository accounts, PasswordEncoder encoder,
                                 SessionUserService sessionUsers, AuditService audit, AccountLabels labels,
                                 BusinessTime time, TransactionOperations transactions, AppProperties properties) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.sessionUsers = sessionUsers;
        this.audit = audit;
        this.labels = labels;
        this.time = time;
        this.transactions = transactions;
        this.loginRules = properties.security().login();
        this.dummyHash = encoder.encode("compte-inexistant-temps-constant");
    }

    /** Une tentative perdante en concurrence (verrou optimiste) est rejouée une fois. */
    public LoginOutcome login(String email, String password) {
        try {
            return transactions.execute(status -> attempt(email, password));
        } catch (ObjectOptimisticLockingFailureException concurrentAttempt) {
            return transactions.execute(status -> attempt(email, password));
        }
    }

    @Transactional
    public void recordLogout(long accountId) {
        accounts.findById(accountId).ifPresent(account -> audit.record(account.getId(), labels.of(account),
                AuditAction.LOGOUT, AuditObjectType.ACCOUNT, String.valueOf(account.getId())));
    }

    private LoginOutcome attempt(String email, String password) {
        Optional<UserAccount> found = accounts.findByEmail(normalize(email));
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            audit.record(null, UNKNOWN_ACTOR, AuditAction.LOGIN_FAILURE, AuditObjectType.ACCOUNT, "inconnu");
            return new LoginOutcome.Rejected(ErrorCode.INVALID_CREDENTIALS, null);
        }
        UserAccount account = found.get();
        Instant now = time.now();
        String reference = String.valueOf(account.getId());
        account.releaseExpiredLock(now);

        if (account.getStatus() == AccountStatus.LOCKED) {
            audit.record(account.getId(), labels.of(account), AuditAction.LOGIN_FAILURE, AuditObjectType.ACCOUNT,
                    reference, Map.of("reason", "ACCOUNT_LOCKED"));
            return new LoginOutcome.Rejected(ErrorCode.ACCOUNT_LOCKED, account.getLockedUntil());
        }
        if (!encoder.matches(password, account.getPasswordHash())) {
            boolean lockedNow = account.registerFailedLogin(now, loginRules.maxFailedAttempts(),
                    loginRules.lockDuration());
            audit.record(account.getId(), labels.of(account), AuditAction.LOGIN_FAILURE, AuditObjectType.ACCOUNT,
                    reference, Map.of("reason", "INVALID_CREDENTIALS"));
            if (lockedNow) {
                audit.record(null, AuditService.SYSTEM_LABEL, AuditAction.STATE_CHANGE, AuditObjectType.ACCOUNT,
                        reference, Map.of("from", "ACTIVE", "to", "LOCKED",
                                "lockedUntil", account.getLockedUntil().toString()));
                return new LoginOutcome.Rejected(ErrorCode.ACCOUNT_LOCKED, account.getLockedUntil());
            }
            return new LoginOutcome.Rejected(ErrorCode.INVALID_CREDENTIALS, null);
        }
        if (account.getStatus() == AccountStatus.DISABLED) {
            audit.record(account.getId(), labels.of(account), AuditAction.LOGIN_FAILURE, AuditObjectType.ACCOUNT,
                    reference, Map.of("reason", "ACCOUNT_DISABLED"));
            return new LoginOutcome.Rejected(ErrorCode.ACCOUNT_DISABLED, null);
        }
        account.registerSuccessfulLogin(now);
        audit.record(account.getId(), labels.of(account), AuditAction.LOGIN_SUCCESS, AuditObjectType.ACCOUNT,
                reference);
        return new LoginOutcome.Authenticated(sessionUsers.describe(account));
    }

    static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
