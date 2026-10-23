package com.bibliotheque.member.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.bibliotheque.member.domain.MemberStatus;

/**
 * Situation de circulation d'un adhérent vue par le module {@code member} (ADR-001 §3) : éligibilité
 * (RG-23) et obligations bloquant l'archivage (RG-18). Implémenté par le module {@code circulation}.
 */
public interface MemberCirculation {

    /** RG-23 : tous les motifs non respectés, quota d'emprunts compris. */
    Eligibility eligibility(long memberId, MemberStatus effectiveStatus);

    Obligations obligations(long memberId);

    /** Obligations de plusieurs adhérents en une lecture (liste « à archiver ») ; absents = aucune obligation. */
    Map<Long, Obligations> obligations(Collection<Long> memberIds);

    /**
     * Éligibilité à l'emprunt. Motifs : {@code MEMBERSHIP_EXPIRED}, {@code MEMBER_SUSPENDED},
     * {@code MEMBER_ARCHIVED}, {@code OVERDUE_LOAN}, {@code UNPAID_FINES_ABOVE_THRESHOLD}, {@code LOAN_LIMIT_REACHED}.
     */
    record Eligibility(boolean eligible, List<String> reasons, int activeLoans, int loanLimit, int remainingLoans,
                       BigDecimal unpaidTotal, BigDecimal fineThreshold) {
    }

    /** RG-18 : emprunts en cours, réservations actives et montant impayé. */
    record Obligations(int activeLoans, int activeReservations, BigDecimal unpaidTotal) {

        public static final Obligations NONE = new Obligations(0, 0, BigDecimal.ZERO.setScale(2));

        public List<String> blockingReasons() {
            List<String> reasons = new ArrayList<>();
            if (activeLoans > 0) {
                reasons.add("ACTIVE_LOANS");
            }
            if (activeReservations > 0) {
                reasons.add("ACTIVE_RESERVATIONS");
            }
            if (unpaidTotal.signum() > 0) {
                reasons.add("UNPAID_FINES");
            }
            return List.copyOf(reasons);
        }
    }
}
