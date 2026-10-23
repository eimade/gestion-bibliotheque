package com.bibliotheque.reporting.web;

import static com.bibliotheque.support.SecurityRequests.authenticated;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.security.SecurityConfig;
import com.bibliotheque.common.security.SecuritySessionManager;
import com.bibliotheque.reporting.service.AuditLogService;
import com.bibliotheque.reporting.service.DashboardService;
import com.bibliotheque.reporting.service.ReportingViews.Dashboard;
import com.bibliotheque.reporting.service.ReportingViews.SettingView;
import com.bibliotheque.reporting.service.SettingsAdminService;
import com.bibliotheque.reporting.service.StatisticsService;

@WebMvcTest(controllers = ReportingController.class)
@Import({SecurityConfig.class, SecuritySessionManager.class})
class ReportingControllerTest {

    private static final AccountPrincipal LIBRARIAN = new AccountPrincipal(2L, "LIBRARIAN", false);
    private static final AccountPrincipal ADMIN = new AccountPrincipal(1L, "ADMIN", false);

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DashboardService dashboard;
    @MockitoBean
    private StatisticsService statistics;
    @MockitoBean
    private SettingsAdminService settings;
    @MockitoBean
    private AuditLogService auditLog;

    @Test
    void tableauDeBordPourLePersonnel() throws Exception {
        when(dashboard.today()).thenReturn(new Dashboard(LocalDate.of(2026, 10, 17), 12, 8, 5, 3, 1, 2,
                Instant.parse("2026-10-17T12:32:00Z")));

        mvc.perform(get("/api/v1/dashboard").with(authenticated(LIBRARIAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loansToday").value(12));
    }

    @Test
    void statistiquesParametresEtAuditReservesALAdministrateur() throws Exception {
        for (String path : List.of("/api/v1/statistics?month=2026-10", "/api/v1/settings", "/api/v1/audit-entries")) {
            mvc.perform(get(path).with(authenticated(LIBRARIAN))).andExpect(status().isForbidden());
        }
    }

    @Test
    void exportTableur() throws Exception {
        when(statistics.export(YearMonth.of(2026, 10))).thenReturn("﻿Statistiques;2026-10\n"
                .getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/v1/statistics/export").param("month", "2026-10").with(authenticated(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"statistiques-2026-10.csv\""))
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv")));
        mvc.perform(get("/api/v1/statistics").param("month", "octobre").with(authenticated(ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modificationDUnParametreEtJournal() throws Exception {
        when(settings.update(eq("LOAN_DURATION_DAYS"), any(), eq(0), eq(1L))).thenReturn(new SettingView(
                "LOAN_DURATION_DAYS", "LOANS", "INTEGER", "DAYS", new BigDecimal("28.00"), BigDecimal.ONE,
                new BigDecimal("90"), null, "LEROY Anne", 1));
        when(auditLog.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(put("/api/v1/settings/LOAN_DURATION_DAYS").with(authenticated(ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":28,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value(28.00));
        mvc.perform(get("/api/v1/audit-entries").param("from", "2026-10-01").param("action", "UPDATE")
                .with(authenticated(ADMIN))).andExpect(status().isOk());
    }
}
