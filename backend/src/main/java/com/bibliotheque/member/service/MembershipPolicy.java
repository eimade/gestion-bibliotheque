package com.bibliotheque.member.service;

import java.time.LocalDate;
import java.time.Period;

import org.springframework.stereotype.Component;

import com.bibliotheque.common.settings.SettingCode;
import com.bibliotheque.common.settings.SettingsService;
import com.bibliotheque.member.domain.Member;
import com.bibliotheque.member.domain.MemberStatus;

/** Règles d'adhésion : durée et renouvellement (RG-15), représentant légal (RG-20). */
@Component
public class MembershipPolicy {

    /** RG-20 : seuil d'âge en dessous duquel un représentant légal est obligatoire. */
    static final int GUARDIAN_AGE_LIMIT = 15;

    private final SettingsService settings;

    public MembershipPolicy(SettingsService settings) {
        this.settings = settings;
    }

    /** Possibilité de renouveler et date qui en résulterait ; {@code blockedBy} est un code d'erreur ou {@code null}. */
    public record Renewal(boolean allowed, LocalDate newExpiryDate, LocalDate renewableFrom, String blockedBy) {
    }

    public LocalDate initialExpiry(LocalDate today) {
        return today.plusMonths(durationMonths());
    }

    /**
     * RG-15 : nouvelle échéance = (la plus tardive entre aujourd'hui et l'échéance actuelle) + durée, possible au
     * plus tôt N jours avant l'échéance. Un adhérent suspendu ou archivé ne se renouvelle pas (AC-020-4).
     */
    public Renewal renewal(Member member, LocalDate today) {
        LocalDate expiry = member.getExpiryDate();
        LocalDate renewableFrom = expiry.minusDays(earlyRenewalDays());
        LocalDate base = expiry.isAfter(today) ? expiry : today;
        LocalDate newExpiry = base.plusMonths(durationMonths());
        if (member.getStatus() == MemberStatus.ARCHIVED) {
            return new Renewal(false, null, null, "MEMBER_ARCHIVED");
        }
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            return new Renewal(false, newExpiry, renewableFrom, "MEMBER_SUSPENDED");
        }
        if (today.isBefore(renewableFrom)) {
            return new Renewal(false, newExpiry, renewableFrom, "RENEWAL_TOO_EARLY");
        }
        return new Renewal(true, newExpiry, renewableFrom, null);
    }

    /** AC-018-3 : adhésion expirant dans N jours au plus, N étant le délai de renouvellement anticipé. */
    public boolean expiresSoon(Member member, LocalDate today) {
        return member.effectiveStatus(today) == MemberStatus.ACTIVE
                && !member.getExpiryDate().isAfter(today.plusDays(earlyRenewalDays()));
    }

    /** RG-20, AC-016-4 : moins de 15 ans révolus à la date du jour (le jour anniversaire, il n'est plus exigé). */
    public boolean requiresGuardian(LocalDate birthDate, LocalDate today) {
        return Period.between(birthDate, today).getYears() < GUARDIAN_AGE_LIMIT;
    }

    private int durationMonths() {
        return settings.getInt(SettingCode.MEMBERSHIP_DURATION_MONTHS);
    }

    private int earlyRenewalDays() {
        return settings.getInt(SettingCode.MEMBERSHIP_EARLY_RENEWAL_DAYS);
    }
}
