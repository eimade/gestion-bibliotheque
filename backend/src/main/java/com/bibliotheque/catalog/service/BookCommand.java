package com.bibliotheque.catalog.service;

import java.util.List;
import java.util.Set;

/** Données d'une notice à créer ou modifier (US-009, US-010) ; {@code version} requise en modification. */
public record BookCommand(String isbn, String title, String subtitle, List<Long> authorIds, Set<Long> categoryIds,
                          String publisher, Integer publicationYear, String languageCode, Integer pageCount,
                          String summary, Integer version) {
}
