package com.bibliotheque.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Découpage du mot-clé (ARCH-catalogue §2) ; l'exécution des critères est testée en intégration (QA). */
class BookSpecificationsTest {

    @Test
    void decoupeSurEspacesTiretsEtApostrophes() {
        assertThat(BookSpecifications.terms("  Monte-Cristo  L'Étranger ")).containsExactly("Monte", "Cristo", "L", "Étranger");
    }

    @Test
    void limiteACinqMots() {
        assertThat(BookSpecifications.terms("un deux trois quatre cinq six sept")).hasSize(BookSpecifications.MAX_TERMS);
    }

    @Test
    void motCleVideSansMot() {
        assertThat(BookSpecifications.terms("   ")).isEmpty();
        assertThat(BookSpecifications.terms(null)).isEmpty();
    }
}
