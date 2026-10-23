package com.bibliotheque.common.exception;

/** Erreur de validation d'un champ, exposée dans {@code ProblemDetail.fieldErrors} (ADR-003). */
public record FieldErrorView(String field, String code, String message) {
}
