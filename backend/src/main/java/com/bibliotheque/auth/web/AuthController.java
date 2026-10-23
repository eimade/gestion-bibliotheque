package com.bibliotheque.auth.web;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.auth.service.AuthenticationService;
import com.bibliotheque.auth.service.LoginOutcome;
import com.bibliotheque.auth.service.PasswordService;
import com.bibliotheque.auth.service.SessionUser;
import com.bibliotheque.auth.service.SessionUserService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.security.SecuritySessionManager;

/** Session de l'utilisateur : connexion, déconnexion, lecture, changement de mot de passe (ARCH-authentification §3). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authentication;
    private final SessionUserService sessionUsers;
    private final PasswordService passwords;
    private final SecuritySessionManager sessions;

    public AuthController(AuthenticationService authentication, SessionUserService sessionUsers,
                          PasswordService passwords, SecuritySessionManager sessions) {
        this.authentication = authentication;
        this.sessionUsers = sessionUsers;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public SessionUserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                     HttpServletResponse response) {
        return switch (authentication.login(body.email(), body.password())) {
            case LoginOutcome.Authenticated(SessionUser user) -> {
                sessions.establish(request, response,
                        new AccountPrincipal(user.accountId(), user.role().name(), user.passwordTemporary()));
                yield SessionUserResponse.from(user);
            }
            case LoginOutcome.Rejected(ErrorCode code, Instant lockedUntil) -> throw rejection(code, lockedUntil);
        };
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) {
        authentication.recordLogout(principal.accountId());
        sessions.close(request);
    }

    @GetMapping("/me")
    public SessionUserResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return SessionUserResponse.from(sessionUsers.current(principal.accountId()));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AccountPrincipal principal,
                               @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request,
                               HttpServletResponse response) {
        passwords.changeOwnPassword(principal.accountId(), body.currentPassword(), body.newPassword());
        sessions.refresh(request, response, principal.withPasswordTemporary(false));
    }

    private static BusinessException rejection(ErrorCode code, Instant lockedUntil) {
        return switch (code) {
            case ACCOUNT_LOCKED -> BusinessException.of(code,
                            "Votre compte est temporairement verrouillé suite à plusieurs tentatives infructueuses.")
                    .withProperty("lockedUntil", lockedUntil.toString());
            case ACCOUNT_DISABLED -> BusinessException.of(code, "Compte désactivé, contactez la bibliothèque.");
            default -> BusinessException.of(ErrorCode.INVALID_CREDENTIALS, "Identifiants incorrects.");
        };
    }
}
