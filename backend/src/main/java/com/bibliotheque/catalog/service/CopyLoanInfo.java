package com.bibliotheque.catalog.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Date de retour prévue des emprunts en cours, vue par le catalogue (AC-008-1). Implémenté par le
 * module {@code circulation}, propriétaire des emprunts (ADR-001 §3).
 */
public interface CopyLoanInfo {

    /** @return date de retour prévue par identifiant d'exemplaire, pour ceux qui ont un emprunt en cours */
    Map<Long, LocalDate> dueDatesOfActiveLoans(Collection<Long> copyIds);
}
