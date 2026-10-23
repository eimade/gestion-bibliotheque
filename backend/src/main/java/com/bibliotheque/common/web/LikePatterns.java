package com.bibliotheque.common.web;

/**
 * Motifs {@code LIKE} « contient » sûrs : les caractères spéciaux saisis par l'utilisateur sont
 * neutralisés avec le caractère d'échappement {@link #ESCAPE} (à déclarer {@code escape '!'} en JPQL).
 */
public final class LikePatterns {

    public static final char ESCAPE = '!';

    private LikePatterns() {
    }

    /** Renvoie {@code null} si la saisie est vide, ce qui désactive le filtre dans les requêtes. */
    public static String contains(String input) {
        return input == null || input.isBlank() ? null : "%" + escape(input) + "%";
    }

    /** Motif « commence par » ; {@code null} si la saisie est vide. */
    public static String startsWith(String input) {
        return input == null || input.isBlank() ? null : escape(input) + "%";
    }

    private static String escape(String input) {
        return input.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
