package com.bibliotheque.auth.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.bibliotheque.common.security.SessionRevoker;

/**
 * Comptes des adhérents, pilotés par le module {@code member} (ARCH-adherents §1) : création à
 * l'inscription (AC-016-1), e-mail de connexion (RG-14, AC-019-1), désactivation à l'archivage (RG-18).
 */
@Service
public class MemberAccountService {

    private final UserAccountRepository accounts;
    private final PasswordService passwords;
    private final SessionRevoker sessionRevoker;
    private final AuditService audit;
    private final BusinessTime time;

    public MemberAccountService(UserAccountRepository accounts, PasswordService passwords,
                                SessionRevoker sessionRevoker, AuditService audit, BusinessTime time) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.sessionRevoker = sessionRevoker;
        this.audit = audit;
        this.time = time;
    }

    /** Compte rattaché à un adhérent ; {@code email} est {@code null} après archivage. */
    public record MemberAccount(long accountId, long memberId, String email, AccountStatus status) {

        static MemberAccount of(UserAccount account) {
            return new MemberAccount(account.getId(), account.getMemberId(), account.getEmail(), account.getStatus());
        }
    }

    public static String normalizeEmail(String email) {
        return AuthenticationService.normalize(email);
    }

    @Transactional(readOnly = true)
    public boolean isEmailUsed(String email) {
        return accounts.existsByEmail(normalizeEmail(email));
    }

    /** RG-01 vérifié avant toute écriture ; le mot de passe est temporaire (RG-05). */
    public void checkPassword(String rawPassword) {
        passwords.hashNewPassword(rawPassword, "password");
    }

    @Transactional
    public MemberAccount create(long memberId, String email, String rawPassword, long actorAccountId) {
        String normalized = normalizeEmail(email);
        if (accounts.existsByEmail(normalized)) {
            throw emailUsed();
        }
        String hash = passwords.hashNewPassword(rawPassword, "password");
        UserAccount account = accounts.save(UserAccount.member(normalized, memberId, hash, time.now()));
        passwords.recordCurrentInHistory(account);
        audit.recordAs(actorAccountId, AuditAction.CREATE, AuditObjectType.ACCOUNT, String.valueOf(account.getId()),
                Map.of("role", AccountRole.MEMBER.name(), "memberId", memberId));
        return MemberAccount.of(account);
    }

    /** AC-019-1 : nouvel e-mail unique (RG-14), qui devient l'identifiant de connexion. */
    @Transactional
    public void changeEmail(long memberId, String email, long actorAccountId) {
        UserAccount account = load(memberId);
        String normalized = normalizeEmail(email);
        if (normalized.equals(account.getEmail())) {
            return;
        }
        if (accounts.existsByEmail(normalized)) {
            throw emailUsed();
        }
        String previous = account.getEmail();
        account.changeEmail(normalized);
        accounts.flush();
        audit.recordAs(actorAccountId, AuditAction.UPDATE, AuditObjectType.ACCOUNT, String.valueOf(account.getId()),
                Map.of("field", "email", "before", previous, "after", normalized));
    }

    /** RG-18 : e-mail effacé, compte désactivé et sessions supprimées. */
    @Transactional
    public void archive(long memberId) {
        UserAccount account = load(memberId);
        account.anonymizeForArchive();
        sessionRevoker.revokeAll(account.getId());
    }

    @Transactional(readOnly = true)
    public Optional<MemberAccount> findByMember(long memberId) {
        return accounts.findByMemberId(memberId).map(MemberAccount::of);
    }

    @Transactional(readOnly = true)
    public Map<Long, MemberAccount> findByMembers(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return accounts.findByMemberIdIn(memberIds).stream()
                .map(MemberAccount::of)
                .collect(Collectors.toMap(MemberAccount::memberId, Function.identity()));
    }

    /** Adhérent rattaché au compte de la session (NFR-06) ; vide pour un compte du personnel. */
    @Transactional(readOnly = true)
    public Optional<Long> memberIdOf(long accountId) {
        return accounts.findById(accountId).map(UserAccount::getMemberId);
    }

    private UserAccount load(long memberId) {
        return accounts.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalStateException("Compte de l'adhérent introuvable : " + memberId));
    }

    private static BusinessException emailUsed() {
        return BusinessException.of(ErrorCode.EMAIL_ALREADY_USED, "Cette adresse e-mail est déjà utilisée.");
    }
}
