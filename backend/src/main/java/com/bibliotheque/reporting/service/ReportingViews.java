package com.bibliotheque.reporting.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Vues du pilotage (records) ; leurs champs sont ceux des DTO d'ARCH-pilotage §3. */
public final class ReportingViews {

    private ReportingViews() {
    }

    public record Dashboard(LocalDate date, long loansToday, long returnsToday, long overdue, long readyReservations,
                            long readyLastDay, long toShelve, Instant computedAt) {
    }

    public record TopBook(long bookId, String title, String authors, long loans) {
    }

    public record Rotation(String category, long loans, long copies, BigDecimal rate) {
    }

    public record Collections(BigDecimal cash, BigDecimal card, BigDecimal check, BigDecimal waived) {
    }

    public record MonthlyStatistics(String month, long loans, long returns, long renewals, long reservations,
                                    long newMembers, long activeBorrowers, List<TopBook> topBooks,
                                    List<Rotation> rotation, Collections collections) {
    }

    public record SettingView(String code, String group, String valueType, String unit, BigDecimal value,
                              BigDecimal min, BigDecimal max, Instant updatedAt, String updatedBy, Integer version) {
    }

    public record AuditEntryView(long id, Instant occurredAt, String actorLabel, String action, String objectType,
                                 String objectRef, JsonNode details) {
    }
}
