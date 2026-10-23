package com.bibliotheque.member.repository;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.bibliotheque.auth.service.MemberDirectory;

/**
 * Fournit au module {@code auth} l'identité d'un adhérent (ADR-001 §3). Lecture JDBC directe :
 * l'entité {@code Member} sera introduite par la feature adherents.
 */
@Repository
public class MemberDirectoryJdbcAdapter implements MemberDirectory {

    private final JdbcClient jdbc;

    public MemberDirectoryJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MemberIdentity> findIdentity(long memberId) {
        return jdbc.sql("SELECT member_number, last_name, first_name FROM members WHERE id = :id")
                .param("id", memberId)
                .query((rs, rowNum) -> new MemberIdentity(rs.getString("member_number"),
                        rs.getString("last_name"), rs.getString("first_name")))
                .optional();
    }
}
