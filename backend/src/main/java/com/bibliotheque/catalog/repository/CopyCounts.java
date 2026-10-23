package com.bibliotheque.catalog.repository;

/** Compteurs d'exemplaires d'une notice (RG-11), calculés pour une page entière en une requête. */
public interface CopyCounts {

    Long getBookId();

    /** Exemplaires {@code AVAILABLE}. */
    Long getAvailable();

    /** Exemplaires hors {@code WITHDRAWN} et {@code LOST}. */
    Long getTotal();

    /** Tous les exemplaires, retirés compris. */
    Long getCopies();

    Long getWithdrawn();
}
