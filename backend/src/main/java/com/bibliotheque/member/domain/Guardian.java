package com.bibliotheque.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Représentant légal d'un adhérent mineur (RG-20) ; les trois champs sont renseignés ensemble. */
@Embeddable
public record Guardian(
        @Column(name = "guardian_last_name", length = 100) String lastName,
        @Column(name = "guardian_first_name", length = 100) String firstName,
        @Column(name = "guardian_phone", length = 20) String phone) {
}
