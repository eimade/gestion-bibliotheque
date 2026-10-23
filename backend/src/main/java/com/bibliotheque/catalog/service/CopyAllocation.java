package com.bibliotheque.catalog.service;

import java.util.List;

/**
 * AC-012-2 / RG-32 : un exemplaire ajouté est attribué à la première réservation en attente de la
 * notice. Implémenté par le module {@code circulation}, propriétaire des réservations (ADR-001 §3).
 */
public interface CopyAllocation {

    /** Appelé dans la transaction d'ajout, une fois les exemplaires enregistrés {@code AVAILABLE}. */
    void copiesAdded(long bookId, List<Long> copyIds, long actorAccountId);
}
