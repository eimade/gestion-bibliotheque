package com.bibliotheque.auth.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.auth.service.AccountPasswordResetService;
import com.bibliotheque.common.security.AccountPrincipal;

/** Réinitialisation d'un mot de passe par le personnel (US-006). */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountPasswordResetService passwordResets;

    public AccountController(AccountPasswordResetService passwordResets) {
        this.passwordResets = passwordResets;
    }

    @PostMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable long id, @Valid @RequestBody PasswordResetRequest body,
                              @AuthenticationPrincipal AccountPrincipal principal) {
        passwordResets.reset(id, body.newPassword(), principal);
    }
}
