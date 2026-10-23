package com.bibliotheque.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bibliotheque.common.security.AccountPrincipal;

/** Requêtes MockMvc d'un utilisateur connecté, avec le même principal qu'en production (ADR-004). */
public final class SecurityRequests {

    private SecurityRequests() {
    }

    public static RequestPostProcessor authenticated(AccountPrincipal principal) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.authorities()));
    }
}
