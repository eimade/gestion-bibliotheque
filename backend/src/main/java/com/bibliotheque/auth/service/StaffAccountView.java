package com.bibliotheque.auth.service;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;

/** Compte du personnel tel que présenté à l'administrateur ; {@code me} = compte de l'appelant. */
public record StaffAccountView(long id, String lastName, String firstName, String email, AccountRole role,
                               AccountStatus status, boolean me, Integer version) {

    static StaffAccountView of(UserAccount account, long currentAccountId) {
        return new StaffAccountView(account.getId(), account.getLastName(), account.getFirstName(),
                account.getEmail(), account.getRole(), account.getStatus(),
                account.getId() == currentAccountId, account.getVersion());
    }
}
