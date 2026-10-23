package com.bibliotheque.circulation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.bibliotheque.common.settings.SettingCode;
import com.bibliotheque.common.settings.SettingsService;

/**
 * Montants des pénalités (RG-35 → RG-37), au centime près sans arrondi intermédiaire (NFR-10). Les paramètres
 * sont relus à chaque calcul : une modification ne s'applique qu'aux pénalités créées ensuite (RG-40).
 */
@Component
public class FineCalculator {

    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final SettingsService settings;

    public FineCalculator(SettingsService settings) {
        this.settings = settings;
    }

    /** RG-36 : jours × tarif journalier, plafonné par emprunt ; 0,00 sans retard. */
    public BigDecimal lateFine(int lateDays) {
        if (lateDays <= 0) {
            return ZERO;
        }
        BigDecimal amount = settings.getAmount(SettingCode.FINE_DAILY_RATE).multiply(BigDecimal.valueOf(lateDays));
        BigDecimal cap = settings.getAmount(SettingCode.FINE_LATE_CAP);
        return amount.min(cap).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** RG-37 : forfait de perte. */
    public BigDecimal lostItemAmount() {
        return settings.getAmount(SettingCode.FINE_LOST_ITEM_AMOUNT);
    }

    /** RG-35 : seuil au-delà duquel (strictement) l'adhérent est bloqué. */
    public BigDecimal blockingThreshold() {
        return settings.getAmount(SettingCode.FINE_BLOCKING_THRESHOLD);
    }
}
