package com.bibliotheque.catalog.domain;

/** Champs descriptifs d'une notice, déjà validés et normalisés (ISBN-13, textes sans espaces superflus). */
public record BookDescription(String isbn, String title, String subtitle, String publisher, Integer publicationYear,
                              Integer pageCount, String summary) {
}
