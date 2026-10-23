package com.bibliotheque.auth.service;

import com.bibliotheque.auth.domain.AccountRole;

/** Données de création d'un compte du personnel (US-004). */
public record CreateStaffAccountCommand(String lastName, String firstName, String email, AccountRole role,
                                        String initialPassword) {
}
