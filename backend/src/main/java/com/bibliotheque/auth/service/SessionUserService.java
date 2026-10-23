package com.bibliotheque.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/** Construit la vue « utilisateur de la session » à partir du compte (et de l'adhérent rattaché). */
@Service
public class SessionUserService {

    private final UserAccountRepository accounts;
    private final MemberDirectory memberDirectory;

    public SessionUserService(UserAccountRepository accounts, MemberDirectory memberDirectory) {
        this.accounts = accounts;
        this.memberDirectory = memberDirectory;
    }

    /** Lecture de la session courante ; un compte désactivé entre-temps n'a plus de session valide. */
    @Transactional(readOnly = true)
    public SessionUser current(long accountId) {
        return accounts.findById(accountId)
                .filter(account -> account.getStatus() != AccountStatus.DISABLED)
                .map(this::describe)
                .orElseThrow(() -> BusinessException.of(ErrorCode.AUTHENTICATION_REQUIRED,
                        "Votre session a expiré. Veuillez vous reconnecter."));
    }

    public SessionUser describe(UserAccount account) {
        if (account.getRole() == AccountRole.MEMBER) {
            return memberDirectory.findIdentity(account.getMemberId())
                    .map(identity -> new SessionUser(account.getId(), account.getEmail(), account.getRole(),
                            identity.lastName(), identity.firstName(), account.isPasswordTemporary(),
                            identity.memberNumber()))
                    .orElseThrow(() -> new IllegalStateException(
                            "Compte adhérent sans adhérent rattaché : " + account.getId()));
        }
        return new SessionUser(account.getId(), account.getEmail(), account.getRole(), account.getLastName(),
                account.getFirstName(), account.isPasswordTemporary(), null);
    }
}
