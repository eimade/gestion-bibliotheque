package com.bibliotheque.common.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Utilisateur connecté, stocké dans la session (ADR-004). Le nom de principal est l'identifiant
 * du compte : il ne change pas quand l'e-mail change et sert à retrouver les sessions d'un compte.
 * Le rôle est porté sous forme de texte pour que {@code common} ne dépende pas du module {@code auth}.
 */
public record AccountPrincipal(long accountId, String role, boolean passwordTemporary)
        implements AuthenticatedPrincipal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return String.valueOf(accountId);
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public boolean hasRole(String expectedRole) {
        return role.equals(expectedRole);
    }

    public AccountPrincipal withPasswordTemporary(boolean temporary) {
        return new AccountPrincipal(accountId, role, temporary);
    }
}
