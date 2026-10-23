package com.bibliotheque.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Langue (donnée de référence, V2 ; Q-22). */
@Entity
@Table(name = "languages")
public class Language {

    @Id
    @Column(name = "code", columnDefinition = "char(3)")
    private String code;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    protected Language() {
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
