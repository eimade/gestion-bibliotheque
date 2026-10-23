package com.bibliotheque.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Ouverture, mise à jour et fermeture de la session de sécurité (ADR-004 §4). La connexion change
 * l'identifiant de session pour empêcher la fixation de session.
 */
@Component
public class SecuritySessionManager {

    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public SecuritySessionManager(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void establish(HttpServletRequest request, HttpServletResponse response, AccountPrincipal principal) {
        request.getSession(true);
        request.changeSessionId();
        save(request, response, principal);
    }

    /** Remplace le principal de la session courante (ex. fin du mot de passe temporaire, RG-05). */
    public void refresh(HttpServletRequest request, HttpServletResponse response, AccountPrincipal principal) {
        save(request, response, principal);
    }

    public void close(HttpServletRequest request) {
        holderStrategy.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private void save(HttpServletRequest request, HttpServletResponse response, AccountPrincipal principal) {
        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
        holderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
