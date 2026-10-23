package com.bibliotheque.member.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** RG-13 : numéro « A » + 6 chiffres tiré de {@code member_number_seq}, jamais réattribué. */
@Repository
public class MemberNumberGenerator {

    private final JdbcClient jdbc;

    public MemberNumberGenerator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        Long value = jdbc.sql("SELECT NEXT VALUE FOR member_number_seq").query(Long.class).single();
        return String.format("A%06d", value);
    }
}
