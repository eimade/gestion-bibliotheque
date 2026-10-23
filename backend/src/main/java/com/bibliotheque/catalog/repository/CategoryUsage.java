package com.bibliotheque.catalog.repository;

import com.bibliotheque.catalog.domain.Category;

/** Catégorie et nombre de notices qui l'utilisent (US-014). */
public record CategoryUsage(Category category, long bookCount) {
}
