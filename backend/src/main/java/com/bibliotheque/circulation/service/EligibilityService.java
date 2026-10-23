package com.bibliotheque.circulation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.Reservation;
import com.bibliotheque.circulation.repository.FineRepository;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.circulation.repository.MemberAmount;
import com.bibliotheque.circulation.repository.MemberCount;
import com.bibliotheque.circulation.repository.ReservationRepository;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.settings.SettingCode;
import com.bibliotheque.common.settings.SettingsService;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberCirculation;

/**
 * RG-23 : éligibilité d'un adhérent, tous les motifs ensemble (AC-025-5). Fournit aussi au module
 * {@code member} les obligations bloquant l'archivage (RG-18, port {@link MemberCirculation}).
 */
@Service
public class EligibilityService implements MemberCirculation {

    public static final String MEMBERSHIP_EXPIRED = "MEMBERSHIP_EXPIRED";
    public static final String MEMBER_SUSPENDED = "MEMBER_SUSPENDED";
    public static final String MEMBER_ARCHIVED = "MEMBER_ARCHIVED";
    public static final String OVERDUE_LOAN = "OVERDUE_LOAN";
    public static final String UNPAID_FINES_ABOVE_THRESHOLD = "UNPAID_FINES_ABOVE_THRESHOLD";
    public static final String LOAN_LIMIT_REACHED = "LOAN_LIMIT_REACHED";

    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final FineRepository fines;
    private final FineCalculator calculator;
    private final SettingsService settings;
    private final BusinessTime time;

    public EligibilityService(LoanRepository loans, ReservationRepository reservations, FineRepository fines,
                              FineCalculator calculator, SettingsService settings, BusinessTime time) {
        this.loans = loans;
        this.reservations = reservations;
        this.fines = fines;
        this.calculator = calculator;
        this.settings = settings;
        this.time = time;
    }

    /** Éligibilité à l'emprunt, quota compris (RG-22). */
    @Override
    @Transactional(readOnly = true)
    public Eligibility eligibility(long memberId, MemberStatus effectiveStatus) {
        return evaluate(memberId, effectiveStatus, true);
    }

    /**
     * @param countQuota {@code false} pour une prolongation ou une réservation : RG-25 et RG-29 appliquent
     *                   RG-23 « hors quota d'emprunts »
     */
    @Transactional(readOnly = true)
    public Eligibility evaluate(long memberId, MemberStatus effectiveStatus, boolean countQuota) {
        LocalDate today = time.today();
        List<String> reasons = new ArrayList<>();
        switch (effectiveStatus) {
            case EXPIRED -> reasons.add(MEMBERSHIP_EXPIRED);
            case SUSPENDED -> reasons.add(MEMBER_SUSPENDED);
            case ARCHIVED -> reasons.add(MEMBER_ARCHIVED);
            case ACTIVE -> {
                // statut conforme
            }
        }
        if (loans.hasOverdueLoan(memberId, today)) {
            reasons.add(OVERDUE_LOAN);
        }
        BigDecimal unpaid = fines.unpaidTotal(memberId);
        BigDecimal threshold = calculator.blockingThreshold();
        if (unpaid.compareTo(threshold) > 0) {
            reasons.add(UNPAID_FINES_ABOVE_THRESHOLD);
        }
        int active = (int) loans.countByMemberIdAndStatusIn(memberId, Loan.ONGOING);
        int limit = settings.getInt(SettingCode.LOAN_MAX_ACTIVE);
        if (countQuota && active >= limit) {
            reasons.add(LOAN_LIMIT_REACHED);
        }
        return new Eligibility(reasons.isEmpty(), List.copyOf(reasons), active, limit, Math.max(0, limit - active),
                unpaid.setScale(2), threshold);
    }

    @Override
    @Transactional(readOnly = true)
    public Obligations obligations(long memberId) {
        return new Obligations((int) loans.countByMemberIdAndStatusIn(memberId, Loan.ONGOING),
                (int) reservations.countByMemberIdAndStatusIn(memberId, Reservation.ACTIVE),
                fines.unpaidTotal(memberId).setScale(2));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Obligations> obligations(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> activeLoans = counts(loans.countByMembers(memberIds, Loan.ONGOING));
        Map<Long, Integer> activeReservations = counts(reservations.countByMembers(memberIds, Reservation.ACTIVE));
        Map<Long, BigDecimal> unpaid = new HashMap<>();
        for (MemberAmount amount : fines.unpaidTotals(memberIds)) {
            unpaid.put(amount.getMemberId(), amount.getTotal());
        }
        Map<Long, Obligations> result = new HashMap<>();
        for (Long memberId : memberIds) {
            result.put(memberId, new Obligations(activeLoans.getOrDefault(memberId, 0),
                    activeReservations.getOrDefault(memberId, 0),
                    unpaid.getOrDefault(memberId, BigDecimal.ZERO).setScale(2)));
        }
        return result;
    }

    private static Map<Long, Integer> counts(List<MemberCount> rows) {
        Map<Long, Integer> counts = new HashMap<>();
        rows.forEach(row -> counts.put(row.getMemberId(), (int) row.getTotal()));
        return counts;
    }
}
