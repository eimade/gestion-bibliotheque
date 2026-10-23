package com.bibliotheque.circulation.job;

import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bibliotheque.circulation.service.LoanMaintenanceService;
import com.bibliotheque.circulation.service.ReservationService;

/**
 * Traitements planifiés de la circulation (ADR-006, heure de Paris) : chaque lot est une transaction
 * courte ; le traitement s'arrête quand un lot est vide.
 */
@Component
public class CirculationJobs {

    private static final Logger LOG = LoggerFactory.getLogger(CirculationJobs.class);

    private final LoanMaintenanceService loans;
    private final ReservationService reservations;

    public CirculationJobs(LoanMaintenanceService loans, ReservationService reservations) {
        this.loans = loans;
        this.reservations = reservations;
    }

    /** RG-27 (00 h 00). */
    @Scheduled(cron = "${app.jobs.overdue-loans-cron}", zone = "${app.jobs.zone}")
    public void markOverdueLoans() {
        LOG.info("Emprunts passés en retard : {}", drain(loans::markNextOverdueBatch));
    }

    /** RG-33, RG-32 (00 h 05, NFR-11 : avant 00 h 15). */
    @Scheduled(cron = "${app.jobs.reservation-expiry-cron}", zone = "${app.jobs.zone}")
    public void expireReservations() {
        LOG.info("Réservations prêtes expirées : {}", drain(reservations::expireNextBatch));
    }

    /** RG-19 (02 h 00). */
    @Scheduled(cron = "${app.jobs.loan-anonymization-cron}", zone = "${app.jobs.zone}")
    public void anonymizeLoans() {
        LOG.info("Emprunts rendus anonymisés : {}", drain(loans::anonymizeNextBatch));
    }

    private static int drain(IntSupplier batch) {
        int total = 0;
        int processed;
        do {
            processed = batch.getAsInt();
            total += processed;
        } while (processed > 0);
        return total;
    }
}
