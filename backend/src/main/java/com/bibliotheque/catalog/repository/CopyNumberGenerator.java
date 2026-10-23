package com.bibliotheque.catalog.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** RG-09 : numéro d'exemplaire tiré de la séquence {@code copy_number_seq}, jamais réattribué, 8 chiffres. */
@Repository
public class CopyNumberGenerator {

    private final JdbcClient jdbc;

    public CopyNumberGenerator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        Long value = jdbc.sql("SELECT NEXT VALUE FOR copy_number_seq").query(Long.class).single();
        return String.format("%08d", value);
    }
}
