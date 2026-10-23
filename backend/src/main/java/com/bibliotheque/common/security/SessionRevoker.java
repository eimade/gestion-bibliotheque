package com.bibliotheque.common.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Supprime toutes les sessions d'un compte : effet immédiat d'une désactivation ou d'une
 * réinitialisation de mot de passe (AC-005-1, ADR-004 §5).
 */
@Component
public class SessionRevoker {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public void revokeAll(long accountId) {
        sessions.findByPrincipalName(String.valueOf(accountId)).keySet().forEach(sessions::deleteById);
    }
}
