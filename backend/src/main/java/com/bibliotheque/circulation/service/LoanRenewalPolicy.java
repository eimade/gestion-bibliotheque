package com.bibliotheque.circulation.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.LoanStatus;
import com.bibliotheque.circulation.service.CirculationViews.RenewalView;
import com.bibliotheque.common.settings.SettingCode;
import com.bibliotheque.common.settings.SettingsService;
import com.bibliotheque.member.service.MemberCirculation.Eligibility;

/**
 * RG-25 : un emprunt se prolonge s'il n'a pas atteint le plafond, n'est pas en retard, n'est réservé par
 * personne et si l'adhérent remplit RG-23 hors quota. Le premier motif bloquant est renvoyé.
 */
@Component
public class LoanRenewalPolicy {

    public static final String RENEWAL_LIMIT_REACHED = "RENEWAL_LIMIT_REACHED";
    public static final String LOAN_OVERDUE = "LOAN_OVERDUE";
    public static final String RENEWAL_BLOCKED_BY_RESERVATION = "RENEWAL_BLOCKED_BY_RESERVATION";
    public static final String MEMBER_NOT_ELIGIBLE = "MEMBER_NOT_ELIGIBLE";

    private final SettingsService settings;

    public LoanRenewalPolicy(SettingsService settings) {
        this.settings = settings;
    }

    /**
     * @param memberEligibility éligibilité de l'adhérent hors quota d'emprunts
     * @param bookReserved      au moins une réservation en attente sur la notice
     */
    public RenewalView evaluate(Loan loan, Eligibility memberEligibility, boolean bookReserved, LocalDate today) {
        LocalDate newDueDate = loan.getDueDate().plusDays(settings.getInt(SettingCode.LOAN_RENEWAL_DAYS));
        if (loan.effectiveStatus(today) == LoanStatus.OVERDUE) {
            return blocked(LOAN_OVERDUE, List.of(), newDueDate);
        }
        if (loan.getRenewalCount() >= settings.getInt(SettingCode.LOAN_MAX_RENEWALS)) {
            return blocked(RENEWAL_LIMIT_REACHED, List.of(), newDueDate);
        }
        if (bookReserved) {
            return blocked(RENEWAL_BLOCKED_BY_RESERVATION, List.of(), newDueDate);
        }
        if (!memberEligibility.eligible()) {
            return blocked(MEMBER_NOT_ELIGIBLE, memberEligibility.reasons(), newDueDate);
        }
        return new RenewalView(true, null, List.of(), newDueDate);
    }

    private static RenewalView blocked(String reason, List<String> memberReasons, LocalDate newDueDate) {
        return new RenewalView(false, reason, memberReasons, newDueDate);
    }
}
