package com.bibliotheque.catalog.domain;

/** Statuts d'un exemplaire (valeurs de {@code ck_copies_status}, cycle de vie INDEX §5). */
public enum CopyStatus {
    AVAILABLE,
    ON_LOAN,
    RESERVED,
    LOST,
    WITHDRAWN
}
