package com.bibliotheque.reporting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.reporting.repository.ReportingQueries;
import com.bibliotheque.reporting.repository.ReportingQueries.TopBookRow;
import com.bibliotheque.reporting.service.ReportingViews.Collections;
import com.bibliotheque.reporting.service.ReportingViews.MonthlyStatistics;
import com.bibliotheque.reporting.service.ReportingViews.Rotation;
import com.bibliotheque.reporting.service.ReportingViews.TopBook;

/**
 * US-042, RG-41 : statistiques d'un mois civil (heure de Paris), données anonymisées comprises, sans aucune
 * donnée nominative ; export tableur (CSV UTF-8 avec BOM, séparateur « ; », lisible par Excel et LibreOffice).
 */
@Service
public class StatisticsService {

    static final int TOP_BOOKS = 10;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final ReportingQueries queries;
    private final BusinessTime time;

    public StatisticsService(ReportingQueries queries, BusinessTime time) {
        this.queries = queries;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public MonthlyStatistics month(YearMonth month) {
        if (month.isAfter(YearMonth.from(time.today()))) {
            throw BusinessException.of(ErrorCode.MONTH_IN_FUTURE, "Sélectionnez un mois passé ou le mois en cours.");
        }
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        Instant from = first.atStartOfDay(time.zone()).toInstant();
        Instant to = last.plusDays(1).atStartOfDay(time.zone()).toInstant();

        List<TopBookRow> top = queries.topBooks(first, last, TOP_BOOKS);
        Map<Long, String> authors = queries.authorsOf(top.stream().map(TopBookRow::bookId).toList());
        List<TopBook> topBooks = top.stream()
                .map(row -> new TopBook(row.bookId(), row.title(), authors.getOrDefault(row.bookId(), ""), row.loans()))
                .toList();
        List<Rotation> rotation = queries.rotation(first, last).stream()
                .map(row -> new Rotation(row.category(), row.loans(), row.copies(), rate(row.loans(), row.copies())))
                .toList();
        Map<String, BigDecimal> collected = queries.collections(from, to);
        Collections collections = new Collections(amount(collected.get("CASH")), amount(collected.get("CARD")),
                amount(collected.get("CHECK")), amount(queries.waivedBetween(from, to)));
        return new MonthlyStatistics(month.toString(), queries.loansBetween(first, last),
                queries.returnsBetween(first, last), queries.renewalsBetween(from, to),
                queries.reservationsBetween(from, to), queries.newMembersBetween(first, last),
                queries.activeBorrowersBetween(first, last), topBooks, rotation, collections);
    }

    /** AC-042-3 : mêmes valeurs qu'à l'écran. */
    @Transactional(readOnly = true)
    public byte[] export(YearMonth month) {
        MonthlyStatistics stats = month(month);
        StringBuilder csv = new StringBuilder("﻿");
        line(csv, "Statistiques", stats.month());
        csv.append('\n');
        line(csv, "Indicateur", "Valeur");
        line(csv, "Emprunts", String.valueOf(stats.loans()));
        line(csv, "Retours", String.valueOf(stats.returns()));
        line(csv, "Prolongations", String.valueOf(stats.renewals()));
        line(csv, "Réservations", String.valueOf(stats.reservations()));
        line(csv, "Nouveaux adhérents", String.valueOf(stats.newMembers()));
        line(csv, "Adhérents ayant emprunté", String.valueOf(stats.activeBorrowers()));
        csv.append('\n');
        line(csv, "Rang", "Titre", "Auteurs", "Emprunts");
        for (int i = 0; i < stats.topBooks().size(); i++) {
            TopBook book = stats.topBooks().get(i);
            line(csv, String.valueOf(i + 1), book.title(), book.authors(), String.valueOf(book.loans()));
        }
        csv.append('\n');
        line(csv, "Catégorie", "Emprunts", "Exemplaires", "Taux de rotation");
        for (Rotation row : stats.rotation()) {
            line(csv, row.category(), String.valueOf(row.loans()), String.valueOf(row.copies()), decimal(row.rate()));
        }
        csv.append('\n');
        line(csv, "Encaissements", "Montant (€)");
        line(csv, "Espèces", decimal(stats.collections().cash()));
        line(csv, "Carte bancaire", decimal(stats.collections().card()));
        line(csv, "Chèque", decimal(stats.collections().check()));
        line(csv, "Remises accordées", decimal(stats.collections().waived()));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** RG-41 : emprunts ÷ exemplaires, arrondi à 2 décimales ; 0 sans exemplaire. */
    static BigDecimal rate(long loans, long copies) {
        if (copies == 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(loans).divide(BigDecimal.valueOf(copies), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String decimal(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.FRANCE));
        return format.format(value);
    }

    private static void line(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(';');
            }
            csv.append(quote(cells[i]));
        }
        csv.append('\n');
    }

    /** Champ CSV entre guillemets si nécessaire ; une cellule commençant par =, +, - ou @ est neutralisée. */
    private static String quote(String value) {
        String cell = value == null ? "" : value;
        if (!cell.isEmpty() && "=+-@".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        if (cell.contains(";") || cell.contains("\"") || cell.contains("\n")) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }
}
