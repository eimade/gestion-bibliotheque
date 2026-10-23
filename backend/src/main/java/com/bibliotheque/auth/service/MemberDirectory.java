package com.bibliotheque.auth.service;

import java.util.Optional;

/**
 * Identité d'un adhérent vue par le module {@code auth} (ADR-001 §3) : implémentée par le module
 * {@code member}, qui détient la table {@code members}.
 */
public interface MemberDirectory {

    Optional<MemberIdentity> findIdentity(long memberId);

    /** Nom et prénom sont {@code null} pour un adhérent archivé (RG-18). */
    record MemberIdentity(String memberNumber, String lastName, String firstName) {
    }
}
