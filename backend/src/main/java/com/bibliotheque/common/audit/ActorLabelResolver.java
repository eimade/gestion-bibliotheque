package com.bibliotheque.common.audit;

/**
 * Libellé d'auteur d'une action à partir de l'identifiant de son compte (ARCH-catalogue §6).
 * Déclaré dans {@code common} et implémenté par le module {@code auth}, pour que les autres modules
 * journalisent sans dépendre de {@code auth} (ADR-001 §3).
 */
public interface ActorLabelResolver {

    String labelOf(long accountId);
}
