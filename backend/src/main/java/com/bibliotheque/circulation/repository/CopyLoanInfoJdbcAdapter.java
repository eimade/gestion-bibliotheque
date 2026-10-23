package com.bibliotheque.circulation.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.bibliotheque.catalog.service.CopyLoanInfo;

/**
 * Fournit au catalogue la date de retour prévue des emprunts en cours (AC-008-1, ADR-001 §3).
 * Lecture JDBC directe : l'entité {@code Loan} sera introduite par la feature emprunts.
 */
@Repository
public class CopyLoanInfoJdbcAdapter implements CopyLoanInfo {

    private final JdbcClient jdbc;

    public CopyLoanInfoJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, LocalDate> dueDatesOfActiveLoans(Collection<Long> copyIds) {
        if (copyIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LocalDate> dueDates = new HashMap<>();
        jdbc.sql("SELECT copy_id, due_date FROM loans WHERE copy_id IN (:ids) AND status IN ('ACTIVE', 'OVERDUE')")
                .param("ids", copyIds)
                .query((rs, rowNum) -> dueDates.put(rs.getLong("copy_id"), rs.getObject("due_date", LocalDate.class)))
                .list();
        return dueDates;
    }
}
