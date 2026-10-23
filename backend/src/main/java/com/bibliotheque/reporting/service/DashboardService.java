package com.bibliotheque.reporting.service;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.reporting.repository.ReportingQueries;
import com.bibliotheque.reporting.repository.ReportingQueries.DashboardCounts;
import com.bibliotheque.reporting.service.ReportingViews.Dashboard;

/** US-041 : indicateurs du jour ; les retards suivent le statut effectif (ADR-006 §3). */
@Service
public class DashboardService {

    private final ReportingQueries queries;
    private final BusinessTime time;

    public DashboardService(ReportingQueries queries, BusinessTime time) {
        this.queries = queries;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Dashboard today() {
        LocalDate today = time.today();
        Instant releasedSince = today.minusDays(1).atStartOfDay(time.zone()).toInstant();
        DashboardCounts counts = queries.dashboard(today, releasedSince);
        return new Dashboard(today, counts.loansToday(), counts.returnsToday(), counts.overdue(), counts.ready(),
                counts.readyLastDay(), counts.toShelve(), time.now());
    }
}
