package com.bibliotheque.common.settings;

/** Paramètres des règles de prêt (RG-40), valeurs initiales dans V2__donnees_de_reference.sql. */
public enum SettingCode {
    MEMBERSHIP_DURATION_MONTHS,
    MEMBERSHIP_EARLY_RENEWAL_DAYS,
    LOAN_DURATION_DAYS,
    LOAN_MAX_ACTIVE,
    LOAN_MAX_RENEWALS,
    LOAN_RENEWAL_DAYS,
    RESERVATION_MAX_ACTIVE,
    RESERVATION_PICKUP_DAYS,
    FINE_BLOCKING_THRESHOLD,
    FINE_DAILY_RATE,
    FINE_LATE_CAP,
    FINE_LOST_ITEM_AMOUNT
}
