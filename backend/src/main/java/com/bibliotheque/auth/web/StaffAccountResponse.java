package com.bibliotheque.auth.web;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.service.StaffAccountView;

public record StaffAccountResponse(Long id, String lastName, String firstName, String email, AccountRole role,
                                   AccountStatus status, boolean me, Integer version) {

    static StaffAccountResponse from(StaffAccountView view) {
        return new StaffAccountResponse(view.id(), view.lastName(), view.firstName(), view.email(), view.role(),
                view.status(), view.me(), view.version());
    }
}
