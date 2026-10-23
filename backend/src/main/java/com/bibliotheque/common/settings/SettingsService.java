package com.bibliotheque.common.settings;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecture typée des paramètres (RG-40). Les valeurs sont relues à chaque opération : une
 * modification s'applique aux opérations suivantes, jamais rétroactivement.
 */
@Service
public class SettingsService {

    private final SettingRepository repository;

    public SettingsService(SettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public int getInt(SettingCode code) {
        Setting setting = load(code);
        requireType(setting, SettingValueType.INTEGER);
        return setting.getValue().intValueExact();
    }

    @Transactional(readOnly = true)
    public BigDecimal getAmount(SettingCode code) {
        Setting setting = load(code);
        requireType(setting, SettingValueType.MONEY);
        return setting.getValue().setScale(2, RoundingMode.UNNECESSARY);
    }

    private Setting load(SettingCode code) {
        return repository.findById(code)
                .orElseThrow(() -> new IllegalStateException("Paramètre absent de la base : " + code));
    }

    private static void requireType(Setting setting, SettingValueType expected) {
        if (setting.getValueType() != expected) {
            throw new IllegalStateException("Le paramètre " + setting.getCode() + " n'est pas de type " + expected);
        }
    }
}
