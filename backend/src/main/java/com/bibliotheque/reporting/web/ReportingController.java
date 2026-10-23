package com.bibliotheque.reporting.web;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.web.PageRequests;
import com.bibliotheque.common.web.PageResponse;
import com.bibliotheque.reporting.service.AuditLogService;
import com.bibliotheque.reporting.service.AuditLogService.Criteria;
import com.bibliotheque.reporting.service.DashboardService;
import com.bibliotheque.reporting.service.ReportingViews.AuditEntryView;
import com.bibliotheque.reporting.service.ReportingViews.Dashboard;
import com.bibliotheque.reporting.service.ReportingViews.MonthlyStatistics;
import com.bibliotheque.reporting.service.ReportingViews.SettingView;
import com.bibliotheque.reporting.service.SettingsAdminService;
import com.bibliotheque.reporting.service.StatisticsService;

/** Pilotage (US-041 → US-044) ; les droits par chemin sont posés par SecurityConfig (ARCH-pilotage §5). */
@RestController
@RequestMapping("/api/v1")
public class ReportingController {

    private final DashboardService dashboard;
    private final StatisticsService statistics;
    private final SettingsAdminService settings;
    private final AuditLogService auditLog;

    public ReportingController(DashboardService dashboard, StatisticsService statistics, SettingsAdminService settings,
                               AuditLogService auditLog) {
        this.dashboard = dashboard;
        this.statistics = statistics;
        this.settings = settings;
        this.auditLog = auditLog;
    }

    record SettingUpdateRequest(@NotNull BigDecimal value, @NotNull Integer version) {
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return dashboard.today();
    }

    @GetMapping("/statistics")
    public MonthlyStatistics statistics(@RequestParam String month) {
        return statistics.month(parseMonth(month));
    }

    @GetMapping("/statistics/export")
    public ResponseEntity<byte[]> export(@RequestParam String month) {
        YearMonth parsed = parseMonth(month);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("statistiques-" + parsed + ".csv").build().toString())
                .body(statistics.export(parsed));
    }

    @GetMapping("/settings")
    public List<SettingView> settings() {
        return settings.list();
    }

    @PutMapping("/settings/{code}")
    public SettingView updateSetting(@PathVariable String code, @Valid @RequestBody SettingUpdateRequest body,
                                     @AuthenticationPrincipal AccountPrincipal principal) {
        return settings.update(code, body.value(), body.version(), principal.accountId());
    }

    @GetMapping("/audit-entries")
    public PageResponse<AuditEntryView> audit(
            @RequestParam(required = false) String object,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.unsorted());
        return PageResponse.from(auditLog.search(new Criteria(object, actor, action, from, to), pageable),
                item -> item);
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw BusinessException.of(ErrorCode.VALIDATION_FAILED, "Le mois doit être au format AAAA-MM.");
        }
    }
}
