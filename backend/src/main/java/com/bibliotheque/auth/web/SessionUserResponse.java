package com.bibliotheque.auth.web;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.service.SessionUser;

public record SessionUserResponse(Long accountId, String email, AccountRole role, String lastName,
                                  String firstName, boolean passwordTemporary, String memberNumber) {

    static SessionUserResponse from(SessionUser user) {
        return new SessionUserResponse(user.accountId(), user.email(), user.role(), user.lastName(),
                user.firstName(), user.passwordTemporary(), user.memberNumber());
    }
}
