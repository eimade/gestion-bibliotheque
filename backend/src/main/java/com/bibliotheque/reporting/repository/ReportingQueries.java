package com.bibliotheque.reporting.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lectures agrégées du pilotage (ARCH-pilotage §1) : requêtes SQL paramétrées en lecture seule, sur toutes
 * les tables. Aucune valeur saisie n'est concaténée dans le SQL.
 */
@Repository
public class ReportingQueries {

    private final JdbcClient jdbc;

    public ReportingQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record DashboardCounts(long loansToday, long returnsToday, long overdue, long ready, long readyLastDay,
                                  long toShelve) {
    }

    public record TopBookRow(long bookId, String title, long loans) {
    }

    public record RotationRow(String category, long loans, long copies) {
    }

    public record AuditRow(long id, Instant occurredAt, String actorLabel, String action, String objectType,
                           String objectRef, String details) {
    }

    /** Filtres du journal d'audit (US-044) ; {@code null} = pas de filtre. */
    public record AuditFilter(String objectRef, String objectPattern, String actorPattern, String action,
                              Instant from, Instant toExclusive) {
    }

    /** US-041 : indicateurs du jour ; « à remettre en rayon » couvre les libérations depuis {@code releasedSince}. */
    public DashboardCounts dashboard(LocalDate today, Instant releasedSince) {
        return jdbc.sql("""
                        SELECT
                          (SELECT COUNT(*) FROM loans WHERE loan_date = :today) AS loans_today,
                          (SELECT COUNT(*) FROM loans WHERE return_date = :today) AS returns_today,
                          (SELECT COUNT(*) FROM loans
                             WHERE status IN ('ACTIVE', 'OVERDUE') AND due_date < :today) AS overdue,
                          (SELECT COUNT(*) FROM reservations WHERE status = 'READY') AS ready,
                          (SELECT COUNT(*) FROM reservations
                             WHERE status = 'READY' AND pickup_deadline = :today) AS ready_last_day,
                          (SELECT COUNT(*) FROM reservations
                             WHERE status IN ('EXPIRED', 'CANCELLED') AND copy_id IS NOT NULL
                               AND closed_at >= :since) AS to_shelve
                        """)
                .param("today", today)
                .param("since", utc(releasedSince))
                .query((rs, rowNum) -> new DashboardCounts(rs.getLong("loans_today"), rs.getLong("returns_today"),
                        rs.getLong("overdue"), rs.getLong("ready"), rs.getLong("ready_last_day"),
                        rs.getLong("to_shelve")))
                .single();
    }

    public long loansBetween(LocalDate first, LocalDate last) {
        return count("SELECT COUNT(*) FROM loans WHERE loan_date BETWEEN :first AND :last", first, last);
    }

    public long returnsBetween(LocalDate first, LocalDate last) {
        return count("SELECT COUNT(*) FROM loans WHERE return_date BETWEEN :first AND :last", first, last);
    }

    public long newMembersBetween(LocalDate first, LocalDate last) {
        return count("SELECT COUNT(*) FROM members WHERE registration_date BETWEEN :first AND :last", first, last);
    }

    /** Q-44 : l'emprunteur d'un emprunt anonymisé (RG-19) n'est plus connu. */
    public long activeBorrowersBetween(LocalDate first, LocalDate last) {
        return count("""
                SELECT COUNT(DISTINCT member_id) FROM loans
                WHERE loan_date BETWEEN :first AND :last AND member_id IS NOT NULL
                """, first, last);
    }

    public long reservationsBetween(Instant from, Instant toExclusive) {
        return jdbc.sql("SELECT COUNT(*) FROM reservations WHERE reserved_at >= :from AND reserved_at < :to")
                .param("from", utc(from)).param("to", utc(toExclusive)).query(Long.class).single();
    }

    /** Q-43 : prolongations comptées par le journal d'audit (événement {@code RENEWAL} sur un emprunt). */
    public long renewalsBetween(Instant from, Instant toExclusive) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM audit_entries
                        WHERE object_type = 'LOAN' AND occurred_at >= :from AND occurred_at < :to
                          AND JSON_VALUE(details, '$.event') = 'RENEWAL'
                        """)
                .param("from", utc(from)).param("to", utc(toExclusive)).query(Long.class).single();
    }

    public List<TopBookRow> topBooks(LocalDate first, LocalDate last, int limit) {
        return jdbc.sql("""
                        SELECT b.id, b.title, COUNT(*) AS loans
                        FROM loans l JOIN copies c ON c.id = l.copy_id JOIN books b ON b.id = c.book_id
                        WHERE l.loan_date BETWEEN :first AND :last
                        GROUP BY b.id, b.title
                        ORDER BY loans DESC, b.title ASC
                        LIMIT :limit
                        """)
                .param("first", first).param("last", last).param("limit", limit)
                .query((rs, rowNum) -> new TopBookRow(rs.getLong("id"), rs.getString("title"), rs.getLong("loans")))
                .list();
    }

    /** Auteurs des notices, dans l'ordre de la notice : « Prénom Nom, Prénom Nom ». */
    public Map<Long, String> authorsOf(Collection<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> names = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT ba.book_id, a.first_name, a.last_name
                        FROM book_authors ba JOIN authors a ON a.id = ba.author_id
                        WHERE ba.book_id IN (:ids)
                        ORDER BY ba.book_id, ba.author_order
                        """)
                .param("ids", bookIds)
                .query((rs, rowNum) -> {
                    String firstName = rs.getString("first_name");
                    String name = firstName == null ? rs.getString("last_name")
                            : firstName + " " + rs.getString("last_name");
                    return names.computeIfAbsent(rs.getLong("book_id"), id -> new ArrayList<>()).add(name);
                })
                .list();
        Map<Long, String> result = new LinkedHashMap<>();
        names.forEach((bookId, list) -> result.put(bookId, String.join(", ", list)));
        return result;
    }

    /** RG-41 : emprunts du mois et exemplaires hors retirés et perdus, par catégorie. */
    public List<RotationRow> rotation(LocalDate first, LocalDate last) {
        return jdbc.sql("""
                        SELECT cat.label,
                          (SELECT COUNT(*) FROM loans l JOIN copies c ON c.id = l.copy_id
                             JOIN book_categories bc ON bc.book_id = c.book_id
                           WHERE bc.category_id = cat.id AND l.loan_date BETWEEN :first AND :last) AS loans,
                          (SELECT COUNT(*) FROM copies c JOIN book_categories bc ON bc.book_id = c.book_id
                           WHERE bc.category_id = cat.id AND c.status NOT IN ('WITHDRAWN', 'LOST')) AS copies
                        FROM categories cat
                        ORDER BY cat.label
                        """)
                .param("first", first).param("last", last)
                .query((rs, rowNum) -> new RotationRow(rs.getString("label"), rs.getLong("loans"),
                        rs.getLong("copies")))
                .list();
    }

    /** Montants encaissés par mode de paiement ({@code CASH}, {@code CARD}, {@code CHECK}). */
    public Map<String, BigDecimal> collections(Instant from, Instant toExclusive) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT payment_method, SUM(amount) AS total FROM payments
                        WHERE collected_at >= :from AND collected_at < :to
                        GROUP BY payment_method
                        """)
                .param("from", utc(from)).param("to", utc(toExclusive))
                .query((rs, rowNum) -> totals.put(rs.getString("payment_method"), rs.getBigDecimal("total")))
                .list();
        return totals;
    }

    public BigDecimal waivedBetween(Instant from, Instant toExclusive) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(amount), 0) FROM fines
                        WHERE status = 'WAIVED' AND settled_at >= :from AND settled_at < :to
                        """)
                .param("from", utc(from)).param("to", utc(toExclusive)).query(BigDecimal.class).single();
    }

    /** US-044 : entrées du plus récent au plus ancien. */
    public List<AuditRow> auditEntries(AuditFilter filter, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, occurred_at, actor_label, action, object_type, object_ref, details
                FROM audit_entries WHERE 1 = 1
                """);
        Map<String, Object> params = auditConditions(filter, sql);
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset");
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql(sql.toString()).params(params)
                .query((rs, rowNum) -> new AuditRow(rs.getLong("id"),
                        rs.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getString("actor_label"), rs.getString("action"), rs.getString("object_type"),
                        rs.getString("object_ref"), rs.getString("details")))
                .list();
    }

    public long countAuditEntries(AuditFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_entries WHERE 1 = 1");
        Map<String, Object> params = auditConditions(filter, sql);
        return jdbc.sql(sql.toString()).params(params).query(Long.class).single();
    }

    /** Ajoute des conditions fixes (seules les valeurs sont paramétrées). */
    private static Map<String, Object> auditConditions(AuditFilter filter, StringBuilder sql) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (filter.objectRef() != null) {
            sql.append(" AND (object_ref = :objectRef OR details LIKE :objectPattern ESCAPE '!')");
            params.put("objectRef", filter.objectRef());
            params.put("objectPattern", filter.objectPattern());
        }
        if (filter.actorPattern() != null) {
            sql.append(" AND actor_label LIKE :actorPattern ESCAPE '!'");
            params.put("actorPattern", filter.actorPattern());
        }
        if (filter.action() != null) {
            sql.append(" AND action = :action");
            params.put("action", filter.action());
        }
        sql.append(" AND occurred_at >= :from AND occurred_at < :to");
        params.put("from", utc(filter.from()));
        params.put("to", utc(filter.toExclusive()));
        return params;
    }

    /** Instants stockés en UTC (DATETIME(6), ADR-002) : paramètre explicite, indépendant du fuseau de la JVM. */
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long count(String sql, LocalDate first, LocalDate last) {
        return jdbc.sql(sql).param("first", first).param("last", last).query(Long.class).single();
    }
}
