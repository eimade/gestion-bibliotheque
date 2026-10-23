package com.bibliotheque.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;

import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.settings.SettingCode;
import com.bibliotheque.common.settings.SettingsService;

/** Paramètres par défaut des règles de prêt (V2__donnees_de_reference.sql) et horloge du 17/10/2026. */
public final class TestSettings {

    private TestSettings() {
    }

    public static SettingsService defaults() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.getInt(SettingCode.MEMBERSHIP_DURATION_MONTHS)).thenReturn(12);
        when(settings.getInt(SettingCode.MEMBERSHIP_EARLY_RENEWAL_DAYS)).thenReturn(30);
        when(settings.getInt(SettingCode.LOAN_DURATION_DAYS)).thenReturn(21);
        when(settings.getInt(SettingCode.LOAN_MAX_ACTIVE)).thenReturn(5);
        when(settings.getInt(SettingCode.LOAN_MAX_RENEWALS)).thenReturn(1);
        when(settings.getInt(SettingCode.LOAN_RENEWAL_DAYS)).thenReturn(21);
        when(settings.getInt(SettingCode.RESERVATION_MAX_ACTIVE)).thenReturn(3);
        when(settings.getInt(SettingCode.RESERVATION_PICKUP_DAYS)).thenReturn(3);
        when(settings.getAmount(SettingCode.FINE_BLOCKING_THRESHOLD)).thenReturn(new BigDecimal("5.00"));
        when(settings.getAmount(SettingCode.FINE_DAILY_RATE)).thenReturn(new BigDecimal("0.20"));
        when(settings.getAmount(SettingCode.FINE_LATE_CAP)).thenReturn(new BigDecimal("10.00"));
        when(settings.getAmount(SettingCode.FINE_LOST_ITEM_AMOUNT)).thenReturn(new BigDecimal("20.00"));
        return settings;
    }

    /** Le 17/10/2026 à 12 h, heure de Paris. */
    public static BusinessTime time() {
        return new BusinessTime(Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC), TestFixtures.properties());
    }

    public static BusinessTime time(Clock clock) {
        return new BusinessTime(clock, TestFixtures.properties());
    }
}
