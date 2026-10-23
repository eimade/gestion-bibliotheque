package com.bibliotheque.reporting.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.config.AppProperties;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.exception.FieldErrorView;
import com.bibliotheque.common.web.LikePatterns;
import com.bibliotheque.reporting.repository.ReportingQueries;
import com.bibliotheque.reporting.repository.ReportingQueries.AuditFilter;
import com.bibliotheque.reporting.repository.ReportingQueries.AuditRow;
import com.bibliotheque.reporting.service.ReportingViews.AuditEntryView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** US-044 : consultation du journal d'audit, en lecture seule (AC-044-3), sur 12 mois au plus (NFR-08). */
@Service
public class AuditLogService {

    private final ReportingQueries queries;
    private final ObjectMapper objectMapper;
    private final BusinessTime time;
    private final AppProperties properties;

    public AuditLogService(ReportingQueries queries, ObjectMapper objectMapper, BusinessTime time,
                           AppProperties properties) {
        this.queries = queries;
        this.objectMapper = objectMapper;
        this.time = time;
        this.properties = properties;
    }

    /** Filtres de la page (ECR-40) ; période par défaut : les 7 derniers jours. */
    public record Criteria(String object, String actor, AuditAction action, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryView> search(Criteria criteria, Pageable pageable) {
        LocalDate today = time.today();
        LocalDate to = criteria.to() == null ? today : criteria.to();
        LocalDate from = criteria.from() == null ? to.minusDays(6) : criteria.from();
        if (from.isAfter(to)) {
            throw invalid("from", "La date de début doit être antérieure ou égale à la date de fin.");
        }
        if (from.isBefore(today.minus(properties.audit().retention()))) {
            throw invalid("from", "Le journal est conservé 12 mois : choisissez une date plus récente.");
        }
        String object = criteria.object() == null || criteria.object().isBlank() ? null : criteria.object().trim();
        AuditFilter filter = new AuditFilter(object, LikePatterns.contains(object),
                LikePatterns.contains(criteria.actor()), criteria.action() == null ? null : criteria.action().name(),
                from.atStartOfDay(time.zone()).toInstant(), to.plusDays(1).atStartOfDay(time.zone()).toInstant());
        long total = queries.countAuditEntries(filter);
        List<AuditEntryView> content = queries.auditEntries(filter, (int) pageable.getOffset(), pageable.getPageSize())
                .stream().map(this::view).toList();
        return new PageImpl<>(content, pageable, total);
    }

    private AuditEntryView view(AuditRow row) {
        return new AuditEntryView(row.id(), row.occurredAt(), row.actorLabel(), row.action(), row.objectType(),
                row.objectRef(), parse(row.details()));
    }

    private JsonNode parse(String details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.readTree(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Détail d'audit illisible", e);
        }
    }

    private static BusinessException invalid(String field, String message) {
        return BusinessException.of(ErrorCode.VALIDATION_FAILED, message)
                .withProperty("fieldErrors", List.of(new FieldErrorView(field, "Invalid", message)));
    }
}
