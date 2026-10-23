package com.bibliotheque.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Catégorie (ENT-Categorie) ; unicité du libellé assurée par la collation (RG-12). */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected Category() {
    }

    public Category(String label) {
        this.label = label;
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public Integer getVersion() {
        return version;
    }
}
