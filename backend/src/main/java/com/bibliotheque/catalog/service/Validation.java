package com.bibliotheque.catalog.service;

import java.util.List;
import java.util.Objects;

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.exception.FieldErrorView;

/** Refus de validation du catalogue au format ADR-003 (400 + {@code fieldErrors}) et contrôle de version. */
final class Validation {

    static final String CONCURRENT_MODIFICATION =
            "Cet élément a été modifié entre-temps par un autre utilisateur.";

    private Validation() {
    }

    static BusinessException invalidField(String field, String message) {
        return BusinessException.of(ErrorCode.VALIDATION_FAILED, "Les données envoyées sont invalides.")
                .withProperty("fieldErrors", List.of(new FieldErrorView(field, "Invalid", message)));
    }

    /** Le client renvoie la version lue : une version différente signifie une modification concurrente (AC-010-3). */
    static void requireVersion(Integer expected, Integer actual) {
        if (expected == null) {
            throw invalidField("version", "La version est obligatoire pour une modification.");
        }
        if (!Objects.equals(expected, actual)) {
            throw BusinessException.of(ErrorCode.CONCURRENT_MODIFICATION, CONCURRENT_MODIFICATION);
        }
    }

    /** Texte facultatif : espaces retirés, chaîne vide ramenée à {@code null}. */
    static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
