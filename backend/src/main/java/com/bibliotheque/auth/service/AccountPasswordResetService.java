package com.bibliotheque.auth.service;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * US-006 : attribution d'un mot de passe temporaire par le personnel. Un bibliothécaire ne peut
 * viser qu'un compte adhérent (RG-06) ; le compte est débloqué et ses sessions sont supprimées.
 */
@Service
public class AccountPasswordResetService {

    private final UserAccountRepository accounts;
    private final PasswordService passwords;
    private final SessionRevoker sessionRevoker;
    private final AuditService audit;
    private final AccountLabels labels;

    public AccountPasswordResetService(UserAccountRepository accounts, PasswordService passwords,
                                       SessionRevoker sessionRevoker, AuditService audit, AccountLabels labels) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.sessionRevoker = sessionRevoker;
        this.audit = audit;
        this.labels = labels;
    }

    @Transactional
    public void reset(long accountId, String newPassword, AccountPrincipal actor) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.ACCOUNT_NOT_FOUND, "Compte introuvable."));
        if (actor.hasRole(AccountRole.LIBRARIAN.name()) && account.getRole() != AccountRole.MEMBER) {
            throw BusinessException.of(ErrorCode.ACCESS_DENIED,
                    "Seul un administrateur peut réinitialiser le mot de passe d'un compte du personnel.");
        }
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw BusinessException.of(ErrorCode.ACCOUNT_DISABLED, "Ce compte est désactivé.")
                    .withStatus(HttpStatus.CONFLICT);
        }
        passwords.assignTemporaryPassword(account, newPassword, "newPassword");
        account.unlock();
        sessionRevoker.revokeAll(account.getId());
        UserAccount actorAccount = accounts.findById(actor.accountId())
                .orElseThrow(() -> new IllegalStateException("Compte de l'appelant introuvable : " + actor.accountId()));
        audit.record(actorAccount.getId(), labels.of(actorAccount), AuditAction.UPDATE, AuditObjectType.ACCOUNT,
                String.valueOf(account.getId()), Map.of("field", "password", "temporary", true));
    }
}
