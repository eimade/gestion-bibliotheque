package com.bibliotheque.common.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/**
 * Accès au temps : instants techniques en UTC, dates métier calendaires à l'heure de la
 * bibliothèque (NFR-11, {@code app.time-zone}).
 */
@Component
public class BusinessTime {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessTime(Clock clock, AppProperties properties) {
        this.clock = clock;
        this.zone = properties.timeZone();
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public ZoneId zone() {
        return zone;
    }
}
