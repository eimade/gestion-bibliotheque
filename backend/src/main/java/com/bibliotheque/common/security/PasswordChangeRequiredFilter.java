package com.bibliotheque.common.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bibliotheque.common.exception.ErrorCode;

/**
 * RG-05 : tant que le mot de passe est temporaire, seules la lecture de la session, le changement
 * de mot de passe et la déconnexion sont autorisés ; toute autre requête d'API reçoit un 403
 * {@code PASSWORD_CHANGE_REQUIRED}.
 */
final class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            "GET /api/v1/auth/me",
            "POST /api/v1/auth/password",
            "POST /api/v1/auth/logout");

    private final ProblemDetailWriter writer;

    PasswordChangeRequiredFilter(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AccountPrincipal principal
                && principal.passwordTemporary()
                && request.getRequestURI().startsWith("/api/")
                && !ALLOWED.contains(request.getMethod() + " " + request.getRequestURI())) {
            writer.write(response, HttpStatus.FORBIDDEN, ErrorCode.PASSWORD_CHANGE_REQUIRED,
                    "Vous devez choisir un nouveau mot de passe avant de continuer.");
            return;
        }
        chain.doFilter(request, response);
    }
}
