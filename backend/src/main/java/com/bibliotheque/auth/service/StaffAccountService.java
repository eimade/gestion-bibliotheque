package com.bibliotheque.auth.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.bibliotheque.common.exception.FieldErrorView;
import com.bibliotheque.common.security.SessionRevoker;
import com.bibliotheque.common.web.LikePatterns;

/** Comptes du personnel (US-004, US-005, RG-04, RG-06, RG-14), réservés à l'administrateur. */
@Service
public class StaffAccountService {

    private static final EnumSet<AccountRole> STAFF_ROLES = EnumSet.of(AccountRole.LIBRARIAN, AccountRole.ADMIN);

    private final UserAccountRepository accounts;
    private final PasswordService passwords;
    private final SessionRevoker sessionRevoker;
    private final AuditService audit;
    private final AccountLabels labels;
    private final BusinessTime time;

    public StaffAccountService(UserAccountRepository accounts, PasswordService passwords,
                               SessionRevoker sessionRevoker, AuditService audit, AccountLabels labels,
                               BusinessTime time) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.sessionRevoker = sessionRevoker;
        this.audit = audit;
        this.labels = labels;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Page<StaffAccountView> search(String query, AccountRole role, AccountStatus status, Pageable pageable,
                                         long currentAccountId) {
        if (role == AccountRole.MEMBER) {
            throw BusinessException.of(ErrorCode.VALIDATION_FAILED, "Les données envoyées sont invalides.")
                    .withProperty("fieldErrors", List.of(new FieldErrorView("role", "Invalid",
                            "Le rôle doit être LIBRARIAN ou ADMIN.")));
        }
        return accounts.searchStaff(STAFF_ROLES, role, status, LikePatterns.contains(query), pageable)
                .map(account -> StaffAccountView.of(account, currentAccountId));
    }

    @Transactional(readOnly = true)
    public StaffAccountView get(long id, long currentAccountId) {
        return StaffAccountView.of(loadStaff(id), currentAccountId);
    }

    @Transactional
    public StaffAccountView create(CreateStaffAccountCommand command, long actorAccountId) {
        if (!command.role().isStaff()) {
            throw BusinessException.of(ErrorCode.ROLE_NOT_ALLOWED,
                    "Les comptes adhérents se créent uniquement à l'inscription de l'adhérent.");
        }
        String email = AuthenticationService.normalize(command.email());
        if (accounts.existsByEmail(email)) {
            throw BusinessException.of(ErrorCode.EMAIL_ALREADY_USED, "Cette adresse e-mail est déjà utilisée.");
        }
        String hash = passwords.hashNewPassword(command.initialPassword(), "initialPassword");
        UserAccount account = accounts.save(UserAccount.staff(email, command.lastName().trim(),
                command.firstName().trim(), command.role(), hash, time.now()));
        passwords.recordCurrentInHistory(account);
        UserAccount actor = loadActor(actorAccountId);
        audit.record(actor.getId(), labels.of(actor), AuditAction.CREATE, AuditObjectType.ACCOUNT,
                String.valueOf(account.getId()), Map.of("email", email, "role", command.role().name()));
        return StaffAccountView.of(account, actorAccountId);
    }

    /** RG-06 : ni soi-même, ni le dernier administrateur ; les sessions du compte sont supprimées. */
    @Transactional
    public StaffAccountView deactivate(long id, long actorAccountId) {
        if (id == actorAccountId) {
            throw BusinessException.of(ErrorCode.SELF_DEACTIVATION_FORBIDDEN,
                    "Vous ne pouvez pas désactiver votre propre compte.");
        }
        UserAccount account = loadStaff(id);
        if (account.getStatus() == AccountStatus.DISABLED) {
            return StaffAccountView.of(account, actorAccountId);
        }
        if (account.getRole() == AccountRole.ADMIN
                && accounts.lockByRoleAndStatusNot(AccountRole.ADMIN, AccountStatus.DISABLED).size() <= 1) {
            throw BusinessException.of(ErrorCode.LAST_ADMIN_REQUIRED,
                    "Au moins un administrateur actif est requis.");
        }
        AccountStatus previous = account.getStatus();
        account.disable();
        sessionRevoker.revokeAll(account.getId());
        recordStateChange(actorAccountId, account, previous);
        return viewAfterWrite(account, actorAccountId);
    }

    @Transactional
    public StaffAccountView reactivate(long id, long actorAccountId) {
        UserAccount account = loadStaff(id);
        if (account.getStatus() == AccountStatus.DISABLED) {
            account.reactivate();
            recordStateChange(actorAccountId, account, AccountStatus.DISABLED);
        }
        return viewAfterWrite(account, actorAccountId);
    }

    /** Écrit les modifications avant de lire la version renvoyée au client (verrou optimiste, ADR-003 §7). */
    private StaffAccountView viewAfterWrite(UserAccount account, long actorAccountId) {
        accounts.flush();
        return StaffAccountView.of(account, actorAccountId);
    }

    private void recordStateChange(long actorAccountId, UserAccount account, AccountStatus previous) {
        UserAccount actor = loadActor(actorAccountId);
        audit.record(actor.getId(), labels.of(actor), AuditAction.STATE_CHANGE, AuditObjectType.ACCOUNT,
                String.valueOf(account.getId()), Map.of("from", previous.name(), "to", account.getStatus().name()));
    }

    private UserAccount loadStaff(long id) {
        return accounts.findById(id)
                .filter(account -> account.getRole().isStaff())
                .orElseThrow(() -> BusinessException.of(ErrorCode.ACCOUNT_NOT_FOUND, "Compte introuvable."));
    }

    private UserAccount loadActor(long actorAccountId) {
        return accounts.findById(actorAccountId)
                .orElseThrow(() -> new IllegalStateException("Compte de l'appelant introuvable : " + actorAccountId));
    }
}
