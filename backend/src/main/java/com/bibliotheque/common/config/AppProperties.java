package com.bibliotheque.common.config;

import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés applicatives {@code app.*} (application.yml). Les expressions cron des traitements
 * planifiés sont lues directement par {@code @Scheduled} et ne figurent pas ici.
 */
@ConfigurationProperties("app")
public record AppProperties(ZoneId timeZone, BootstrapAdmin bootstrapAdmin, Security security, Audit audit,
                            Privacy privacy) {

    /** Administrateur initial créé au démarrage si aucun administrateur n'existe (ADR-004 §10). */
    public record BootstrapAdmin(String email, String password) {
    }

    public record Security(Login login, Password password) {

        /** RG-02 : verrouillage après échecs consécutifs. */
        public record Login(int maxFailedAttempts, Duration lockDuration) {
        }

        /** RG-01 : longueur minimale et taille de l'historique. */
        public record Password(int minLength, int historySize) {
        }
    }

    /** RG-19 : effacement de l'emprunteur (D2) et proposition d'archivage des adhérents (D1). */
    public record Privacy(Period loanHistoryRetention, Period memberArchiveProposalAfter) {
    }

    /** NFR-08 : durée de conservation du journal d'audit. */
    public record Audit(Period retention) {
    }
}
