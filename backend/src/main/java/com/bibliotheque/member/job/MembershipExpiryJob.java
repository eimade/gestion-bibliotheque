package com.bibliotheque.member.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bibliotheque.member.service.MembershipExpiryService;

/** RG-16 : passage quotidien des adhésions échues à l'état expiré (ADR-006, 00 h 10). */
@Component
public class MembershipExpiryJob {

    private static final Logger LOG = LoggerFactory.getLogger(MembershipExpiryJob.class);

    private final MembershipExpiryService service;

    public MembershipExpiryJob(MembershipExpiryService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.jobs.membership-expiry-cron}", zone = "${app.jobs.zone}")
    public void run() {
        int total = 0;
        int batch;
        do {
            batch = service.expireNextBatch();
            total += batch;
        } while (batch > 0);
        LOG.info("Adhésions échues passées à l'état expiré : {}", total);
    }
}
