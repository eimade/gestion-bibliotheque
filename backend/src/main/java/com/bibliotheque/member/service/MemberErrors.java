package com.bibliotheque.member.service;

import java.util.List;
import java.util.Objects;

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.exception.FieldErrorView;

/** Refus récurrents du module (messages exacts d'UX-adherents). */
final class MemberErrors {

    private MemberErrors() {
    }

    static BusinessException notFound(String memberNumber) {
        return BusinessException.of(ErrorCode.MEMBER_NOT_FOUND,
                "Aucun adhérent ne porte le numéro " + memberNumber + ".");
    }

    static BusinessException invalidField(String field, String message) {
        return BusinessException.of(ErrorCode.VALIDATION_FAILED, "Les données envoyées sont invalides.")
                .withProperty("fieldErrors", List.of(new FieldErrorView(field, "Invalid", message)));
    }

    /** Verrou optimiste explicite : le client renvoie la version lue (ADR-003 §7). */
    static void requireVersion(Integer expected, Integer actual) {
        if (expected == null) {
            throw invalidField("version", "La version est obligatoire pour une modification.");
        }
        if (!Objects.equals(expected, actual)) {
            throw BusinessException.of(ErrorCode.CONCURRENT_MODIFICATION,
                    "Cet élément a été modifié entre-temps par un autre utilisateur.");
        }
    }
}
