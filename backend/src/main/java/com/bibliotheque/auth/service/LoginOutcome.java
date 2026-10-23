package com.bibliotheque.auth.service;

import java.time.Instant;

import com.bibliotheque.common.exception.ErrorCode;

/**
 * Résultat d'une tentative de connexion. Un refus est un résultat, pas une exception : la
 * transaction est validée pour conserver le compteur d'échecs et le journal (RG-02, NFR-08).
 */
public sealed interface LoginOutcome {

    record Authenticated(SessionUser user) implements LoginOutcome {
    }

    /** @param lockedUntil fin du verrouillage, renseignée seulement pour {@code ACCOUNT_LOCKED} */
    record Rejected(ErrorCode code, Instant lockedUntil) implements LoginOutcome {
    }
}
