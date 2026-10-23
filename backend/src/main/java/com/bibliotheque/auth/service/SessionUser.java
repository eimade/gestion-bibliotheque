package com.bibliotheque.auth.service;

import com.bibliotheque.auth.domain.AccountRole;

/** Utilisateur de la session (UX-socle §8) ; {@code memberNumber} renseigné pour un adhérent. */
public record SessionUser(long accountId, String email, AccountRole role, String lastName, String firstName,
                          boolean passwordTemporary, String memberNumber) {
}
