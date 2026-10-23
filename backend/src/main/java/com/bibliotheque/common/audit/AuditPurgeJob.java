package com.bibliotheque.common.audit;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import com.bibliotheque.common.config.AppProperties;
import com.bibliotheque.common.config.BusinessTime;

/**
 * Purge quotidienne du journal d'audit au-delà de la durée de conservation (NFR-08, ADR-006).
 * Le résumé est écrit dans le journal applicatif : la liste fermée des types d'objet d'audit ne
 * prévoit pas le journal lui-même (écart Q-32).
 */
@Component
public class AuditPurgeJob {

    private static final Logger LOG = LoggerFactory.getLogger(AuditPurgeJob.class);

    private final AuditEntryRepository repository;
    private final TransactionOperations transactions;
    private final BusinessTime time;
    private final AppProperties properties;

    public AuditPurgeJob(AuditEntryRepository repository, TransactionOperations transactions, BusinessTime time,
                         AppProperties properties) {
        this.repository = repository;
        this.transactions = transactions;
        this.time = time;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.jobs.audit-purge-cron}", zone = "${app.jobs.zone}")
    public void purge() {
        Instant limit = time.now().atZone(time.zone()).minus(properties.audit().retention()).toInstant();
        int total = 0;
        int deleted;
        do {
            Integer batch = transactions.execute(status -> repository.deleteBatchOlderThan(limit));
            deleted = batch == null ? 0 : batch;
            total += deleted;
        } while (deleted > 0);
        LOG.info("Purge du journal d'audit antérieur au {} : {} entrée(s) supprimée(s)", limit, total);
    }
}
