package com.bibliotheque.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.auth.domain.PasswordHistoryEntry;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.PasswordHistoryEntryRepository;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.AppProperties;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/** Mots de passe : politique (RG-01), historique des 3 derniers, caractère temporaire (RG-05). */
@Service
public class PasswordService {

    private final UserAccountRepository accounts;
    private final PasswordHistoryEntryRepository history;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final BusinessTime time;
    private final AuditService audit;
    private final AccountLabels labels;
    private final int historySize;

    public PasswordService(UserAccountRepository accounts, PasswordHistoryEntryRepository history,
                           PasswordEncoder encoder, PasswordPolicy policy, BusinessTime time, AuditService audit,
                           AccountLabels labels, AppProperties properties) {
        this.accounts = accounts;
        this.history = history;
        this.encoder = encoder;
        this.policy = policy;
        this.time = time;
        this.audit = audit;
        this.labels = labels;
        this.historySize = properties.security().password().historySize();
    }

    /** US-003 : changement par l'utilisateur ; le mot de passe cesse d'être temporaire. */
    @Transactional
    public void changeOwnPassword(long accountId, String currentPassword, String newPassword) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.ACCOUNT_NOT_FOUND, "Compte introuvable."));
        if (!encoder.matches(currentPassword, account.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.CURRENT_PASSWORD_INCORRECT, "Mot de passe actuel incorrect.");
        }
        policy.check(newPassword, "newPassword");
        if (isRecentlyUsed(account, newPassword)) {
            throw BusinessException.of(ErrorCode.PASSWORD_RECENTLY_USED,
                    "Mot de passe déjà utilisé récemment. Choisissez-en un autre.");
        }
        apply(account, newPassword, false);
        audit.record(account.getId(), labels.of(account), AuditAction.UPDATE, AuditObjectType.ACCOUNT,
                String.valueOf(account.getId()), Map.of("field", "password"));
    }

    /** Vérifie la politique puis renvoie l'empreinte d'un mot de passe initial. */
    public String hashNewPassword(String rawPassword, String field) {
        policy.check(rawPassword, field);
        return encoder.encode(rawPassword);
    }

    /** Mot de passe défini par un tiers : temporaire (RG-05). L'appelant journalise l'opération. */
    @Transactional
    public void assignTemporaryPassword(UserAccount account, String rawPassword, String field) {
        policy.check(rawPassword, field);
        apply(account, rawPassword, true);
    }

    /** Enregistre l'empreinte courante du compte dans l'historique (compte déjà persisté). */
    @Transactional
    public void recordCurrentInHistory(UserAccount account) {
        history.save(new PasswordHistoryEntry(account.getId(), account.getPasswordHash(), time.now()));
        prune(account.getId());
    }

    private void apply(UserAccount account, String rawPassword, boolean temporary) {
        Instant now = time.now();
        account.changePassword(encoder.encode(rawPassword), temporary, now);
        recordCurrentInHistory(account);
    }

    private boolean isRecentlyUsed(UserAccount account, String rawPassword) {
        if (encoder.matches(rawPassword, account.getPasswordHash())) {
            return true;
        }
        return history.findByUserAccountIdOrderByCreatedAtDescIdDesc(account.getId()).stream()
                .limit(historySize)
                .anyMatch(entry -> encoder.matches(rawPassword, entry.getPasswordHash()));
    }

    private void prune(Long accountId) {
        List<PasswordHistoryEntry> entries = history.findByUserAccountIdOrderByCreatedAtDescIdDesc(accountId);
        if (entries.size() > historySize) {
            history.deleteAll(entries.subList(historySize, entries.size()));
        }
    }
}
