package com.bibliotheque.catalog.service;

import java.util.List;
import java.util.Optional;

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.exception.FieldErrorView;

/**
 * RG-07 : ISBN-10 ou ISBN-13 à clé de contrôle valide, tirets et espaces ignorés, ISBN-10 converti
 * en ISBN-13 (préfixe 978).
 */
public final class Isbn {

    private Isbn() {
    }

    /** @return l'ISBN-13 normalisé, ou vide si la saisie n'est pas un ISBN valide */
    public static Optional<String> toIsbn13(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String compact = raw.replaceAll("[\\s-]", "").toUpperCase();
        if (compact.matches("97[89]\\d{10}") && isbn13CheckDigit(compact.substring(0, 12)) == compact.charAt(12)) {
            return Optional.of(compact);
        }
        if (compact.matches("\\d{9}[\\dX]") && isValidIsbn10(compact)) {
            String body = "978" + compact.substring(0, 9);
            return Optional.of(body + isbn13CheckDigit(body));
        }
        return Optional.empty();
    }

    /** Saisie facultative : {@code null} si vide, ISBN-13 si valide, sinon 422 {@code ISBN_INVALID}. */
    public static String normalizeOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return toIsbn13(raw).orElseThrow(() -> BusinessException.of(ErrorCode.ISBN_INVALID, "ISBN invalide.")
                .withProperty("field", "isbn")
                .withProperty("fieldErrors", List.of(new FieldErrorView("isbn", "Isbn", "ISBN invalide."))));
    }

    private static char isbn13CheckDigit(String twelveDigits) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = twelveDigits.charAt(i) - '0';
            sum += i % 2 == 0 ? digit : digit * 3;
        }
        return (char) ('0' + (10 - sum % 10) % 10);
    }

    private static boolean isValidIsbn10(String isbn10) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = isbn10.charAt(i);
            int value = c == 'X' ? 10 : c - '0';
            if (c == 'X' && i != 9) {
                return false;
            }
            sum += value * (10 - i);
        }
        return sum % 11 == 0;
    }
}
