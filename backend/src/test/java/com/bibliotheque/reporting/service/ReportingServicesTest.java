package com.bibliotheque.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.common.audit.ActorLabelResolver;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.settings.Setting;
import com.bibliotheque.common.settings.SettingCode;
import com.bibliotheque.common.settings.SettingRepository;
import com.bibliotheque.common.settings.SettingValueType;
import com.bibliotheque.reporting.repository.ReportingQueries;
import com.bibliotheque.reporting.repository.ReportingQueries.AuditFilter;
import com.bibliotheque.reporting.repository.ReportingQueries.AuditRow;
import com.bibliotheque.reporting.repository.ReportingQueries.DashboardCounts;
import com.bibliotheque.reporting.repository.ReportingQueries.RotationRow;
import com.bibliotheque.reporting.repository.ReportingQueries.TopBookRow;
import com.bibliotheque.reporting.service.AuditLogService.Criteria;
import com.bibliotheque.reporting.service.ReportingViews.MonthlyStatistics;
import com.bibliotheque.support.TestFixtures;
import com.bibliotheque.support.TestSettings;
import com.fasterxml.jackson.databind.ObjectMapper;

class ReportingServicesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private final ReportingQueries queries = mock(ReportingQueries.class);

    @Test
    void tableauDeBordDuJour() {
        when(queries.dashboard(eq(TODAY), any())).thenReturn(new DashboardCounts(12, 8, 5, 3, 1, 2));

        var dashboard = new DashboardService(queries, TestSettings.time()).today();

        assertThat(dashboard.date()).isEqualTo(TODAY);
        assertThat(dashboard.loansToday()).isEqualTo(12);
        assertThat(dashboard.readyLastDay()).isEqualTo(1);
        assertThat(dashboard.toShelve()).isEqualTo(2);
        verify(queries).dashboard(TODAY, Instant.parse("2026-10-15T22:00:00Z"));
    }

    @Test
    void statistiquesDuMoisAvecRotationEtEncaissements() {
        stubMonth();

        MonthlyStatistics stats = new StatisticsService(queries, TestSettings.time()).month(YearMonth.of(2026, 10));

        assertThat(stats.loans()).isEqualTo(40);
        assertThat(stats.topBooks()).singleElement().satisfies(book -> assertThat(book.authors()).isEqualTo("Albert Camus"));
        assertThat(stats.rotation()).extracting(ReportingViews.Rotation::rate)
                .containsExactly(new BigDecimal("0.85"), new BigDecimal("0.00"));
        assertThat(stats.collections().card()).isEqualTo(new BigDecimal("310.00"));
        assertThat(stats.collections().check()).isEqualTo(new BigDecimal("0.00"));
        verify(queries).renewalsBetween(Instant.parse("2026-09-30T22:00:00Z"), Instant.parse("2026-10-31T23:00:00Z"));
    }

    @Test
    void moisFuturRefuse() {
        assertThatThrownBy(() -> new StatisticsService(queries, TestSettings.time()).month(YearMonth.of(2026, 11)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MONTH_IN_FUTURE));
    }

    @Test
    void exportTableurSansDonneeNominativeEtProtegeContreLesFormules() {
        stubMonth();
        when(queries.topBooks(any(), any(), anyInt())).thenReturn(List.of(new TopBookRow(3L, "=Calcul; piégé", 38)));

        String csv = new String(new StatisticsService(queries, TestSettings.time()).export(YearMonth.of(2026, 10)),
                StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿Statistiques;2026-10");
        assertThat(csv).contains("Emprunts;40").contains("Roman;34;40;0,85").contains("Carte bancaire;310,00");
        assertThat(csv).contains("1;\"'=Calcul; piégé\";Albert Camus;38");
    }

    @Test
    void parametreModifieDansSesBornesEtJournalise() {
        SettingRepository repository = mock(SettingRepository.class);
        AuditService audit = mock(AuditService.class);
        ActorLabelResolver labels = mock(ActorLabelResolver.class);
        when(labels.labelOf(1L)).thenReturn("LEROY Anne");
        Setting duration = setting(SettingCode.LOAN_DURATION_DAYS, SettingValueType.INTEGER, "21.00", "1.00", "90.00");
        Setting rate = setting(SettingCode.FINE_DAILY_RATE, SettingValueType.MONEY, "0.20", "0.00", "5.00");
        when(repository.findById(SettingCode.LOAN_DURATION_DAYS)).thenReturn(Optional.of(duration));
        when(repository.findById(SettingCode.FINE_DAILY_RATE)).thenReturn(Optional.of(rate));
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(duration, rate));
        SettingsAdminService service = new SettingsAdminService(repository, labels, audit, TestSettings.time());

        var updated = service.update("LOAN_DURATION_DAYS", new BigDecimal("28"), 0, 1L);

        assertThat(updated.value()).isEqualTo(new BigDecimal("28.00"));
        assertThat(updated.updatedBy()).isEqualTo("LEROY Anne");
        verify(audit).recordAs(eq(1L), eq(AuditAction.UPDATE), eq(AuditObjectType.SETTING), eq("LOAN_DURATION_DAYS"),
                anyMap());
        assertThat(service.list()).hasSize(2);
        assertRefused(() -> service.update("LOAN_DURATION_DAYS", new BigDecimal("91"), 0, 1L),
                ErrorCode.SETTING_OUT_OF_RANGE);
        assertRefused(() -> service.update("LOAN_DURATION_DAYS", new BigDecimal("2.5"), 0, 1L),
                ErrorCode.VALIDATION_FAILED);
        assertRefused(() -> service.update("FINE_DAILY_RATE", new BigDecimal("0.205"), 0, 1L),
                ErrorCode.VALIDATION_FAILED);
        assertRefused(() -> service.update("FINE_DAILY_RATE", new BigDecimal("0.30"), 4, 1L),
                ErrorCode.CONCURRENT_MODIFICATION);
        assertRefused(() -> service.update("INCONNU", BigDecimal.ONE, 0, 1L), ErrorCode.ROUTE_NOT_FOUND);
        assertThat(service.update("FINE_DAILY_RATE", new BigDecimal("0.3"), 0, 1L).value())
                .isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void journalDAuditFiltreSurSeptJoursParDefaut() {
        AuditLogService service = new AuditLogService(queries, new ObjectMapper(), TestSettings.time(),
                TestFixtures.properties());
        when(queries.countAuditEntries(any())).thenReturn(1L);
        when(queries.auditEntries(any(), eq(0), eq(20))).thenReturn(List.of(new AuditRow(1L, TestFixtures.NOW,
                "DUPONT Marie", "UPDATE", "MEMBER", "A000123", "{\"phone\":{\"before\":\"1\",\"after\":\"2\"}}")));

        var page = service.search(new Criteria(" A000123 ", "dupont", AuditAction.UPDATE, null, null),
                PageRequest.of(0, 20));

        assertThat(page.getContent().get(0).details().get("phone").get("after").asText()).isEqualTo("2");
        ArgumentCaptor<AuditFilter> filter = ArgumentCaptor.forClass(AuditFilter.class);
        verify(queries).countAuditEntries(filter.capture());
        assertThat(filter.getValue().objectRef()).isEqualTo("A000123");
        assertThat(filter.getValue().actorPattern()).isEqualTo("%dupont%");
        assertThat(filter.getValue().from()).isEqualTo(Instant.parse("2026-10-10T22:00:00Z"));
    }

    @Test
    void journalDAuditPeriodeInvalide() {
        AuditLogService service = new AuditLogService(queries, new ObjectMapper(), TestSettings.time(),
                TestFixtures.properties());

        assertRefused(() -> service.search(new Criteria(null, null, null, TODAY, TODAY.minusDays(1)),
                PageRequest.of(0, 20)), ErrorCode.VALIDATION_FAILED);
        assertRefused(() -> service.search(new Criteria(null, null, null, TODAY.minusMonths(13), TODAY),
                PageRequest.of(0, 20)), ErrorCode.VALIDATION_FAILED);
    }

    private void stubMonth() {
        when(queries.loansBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).thenReturn(40L);
        when(queries.topBooks(any(), any(), anyInt())).thenReturn(List.of(new TopBookRow(3L, "L'Étranger", 38)));
        when(queries.authorsOf(List.of(3L))).thenReturn(Map.of(3L, "Albert Camus"));
        when(queries.rotation(any(), any())).thenReturn(List.of(new RotationRow("Roman", 34, 40),
                new RotationRow("Jeunesse", 0, 0)));
        when(queries.collections(any(), any())).thenReturn(Map.of("CASH", new BigDecimal("120.4"),
                "CARD", new BigDecimal("310")));
        when(queries.waivedBetween(any(), any())).thenReturn(new BigDecimal("60"));
    }

    private static Setting setting(SettingCode code, SettingValueType type, String value, String min, String max) {
        Setting setting = BeanUtils.instantiateClass(Setting.class);
        ReflectionTestUtils.setField(setting, "code", code);
        ReflectionTestUtils.setField(setting, "valueType", type);
        ReflectionTestUtils.setField(setting, "settingGroup", "LOANS");
        ReflectionTestUtils.setField(setting, "unit", "DAYS");
        ReflectionTestUtils.setField(setting, "value", new BigDecimal(value));
        ReflectionTestUtils.setField(setting, "minValue", new BigDecimal(min));
        ReflectionTestUtils.setField(setting, "maxValue", new BigDecimal(max));
        ReflectionTestUtils.setField(setting, "version", 0);
        return setting;
    }

    private static void assertRefused(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }
}
