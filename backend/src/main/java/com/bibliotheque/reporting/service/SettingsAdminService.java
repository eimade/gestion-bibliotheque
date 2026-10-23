package com.bibliotheque.reporting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.common.audit.ActorLabelResolver;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.exception.FieldErrorView;
import com.bibliotheque.common.settings.Setting;
import com.bibliotheque.common.settings.SettingCode;
import com.bibliotheque.common.settings.SettingRepository;
import com.bibliotheque.common.settings.SettingValueType;
import com.bibliotheque.reporting.service.ReportingViews.SettingView;

/** US-043, RG-40 : paramètres des règles de prêt, modifiables dans leurs bornes, sans effet rétroactif. */
@Service
public class SettingsAdminService {

    private final SettingRepository settings;
    private final ActorLabelResolver labels;
    private final AuditService audit;
    private final BusinessTime time;

    public SettingsAdminService(SettingRepository settings, ActorLabelResolver labels, AuditService audit,
                                BusinessTime time) {
        this.settings = settings;
        this.labels = labels;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public List<SettingView> list() {
        return settings.findAll(Sort.by("displayOrder")).stream().map(this::view).toList();
    }

    @Transactional
    public SettingView update(String code, BigDecimal value, Integer version, long adminAccountId) {
        SettingCode settingCode = Arrays.stream(SettingCode.values())
                .filter(candidate -> candidate.name().equals(code))
                .findFirst()
                .orElseThrow(() -> BusinessException.of(ErrorCode.ROUTE_NOT_FOUND, "Paramètre inconnu."));
        Setting setting = settings.findById(settingCode)
                .orElseThrow(() -> BusinessException.of(ErrorCode.ROUTE_NOT_FOUND, "Paramètre inconnu."));
        if (!Objects.equals(version, setting.getVersion())) {
            throw BusinessException.of(ErrorCode.CONCURRENT_MODIFICATION,
                    "Cet élément a été modifié entre-temps par un autre utilisateur.");
        }
        BigDecimal normalized = normalize(setting, value);
        if (normalized.compareTo(setting.getMinValue()) < 0 || normalized.compareTo(setting.getMaxValue()) > 0) {
            throw BusinessException.of(ErrorCode.SETTING_OUT_OF_RANGE, "La valeur doit être comprise entre "
                            + setting.getMinValue().stripTrailingZeros().toPlainString() + " et "
                            + setting.getMaxValue().stripTrailingZeros().toPlainString() + ".")
                    .withProperty("min", setting.getMinValue())
                    .withProperty("max", setting.getMaxValue());
        }
        BigDecimal previous = setting.getValue();
        setting.update(normalized, adminAccountId, time.now());
        settings.flush();
        audit.recordAs(adminAccountId, AuditAction.UPDATE, AuditObjectType.SETTING, code,
                Map.of("before", previous, "after", normalized));
        return view(setting);
    }

    /** Entier sans décimale, ou montant à 2 décimales au plus (« Saisissez un nombre entier. »). */
    private static BigDecimal normalize(Setting setting, BigDecimal value) {
        if (setting.getValueType() == SettingValueType.INTEGER) {
            if (value.stripTrailingZeros().scale() > 0) {
                throw invalidValue("Saisissez un nombre entier.");
            }
            return value.setScale(2);
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw invalidValue("Saisissez un montant valide (ex. 0,20).");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BusinessException invalidValue(String message) {
        return BusinessException.of(ErrorCode.VALIDATION_FAILED, "Les données envoyées sont invalides.")
                .withProperty("fieldErrors", List.of(new FieldErrorView("value", "Invalid", message)));
    }

    private SettingView view(Setting setting) {
        return new SettingView(setting.getCode().name(), setting.getSettingGroup(), setting.getValueType().name(),
                setting.getUnit(), setting.getValue(), setting.getMinValue(), setting.getMaxValue(),
                setting.getUpdatedAt(), setting.getUpdatedBy() == null ? null : labels.labelOf(setting.getUpdatedBy()),
                setting.getVersion());
    }
}
