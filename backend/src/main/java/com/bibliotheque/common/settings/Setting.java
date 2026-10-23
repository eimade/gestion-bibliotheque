package com.bibliotheque.common.settings;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Paramètre des règles de prêt (RG-40), modifiable par l'administrateur dans ses bornes. La nouvelle valeur
 * ne s'applique qu'aux opérations postérieures.
 */
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", length = 50)
    private SettingCode code;

    @Column(name = "setting_group", nullable = false, length = 20)
    private String settingGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 10)
    private SettingValueType valueType;

    @Column(name = "unit", nullable = false, length = 10)
    private String unit;

    @Column(name = "setting_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "min_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal minValue;

    @Column(name = "max_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxValue;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected Setting() {
    }

    /** RG-40 : les bornes et le type sont vérifiés par l'appelant (et par {@code ck_settings_*}). */
    public void update(BigDecimal newValue, long actorAccountId, Instant now) {
        this.value = newValue;
        this.updatedBy = actorAccountId;
        this.updatedAt = now;
    }

    public String getSettingGroup() {
        return settingGroup;
    }

    public String getUnit() {
        return unit;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public SettingCode getCode() {
        return code;
    }

    public SettingValueType getValueType() {
        return valueType;
    }

    public BigDecimal getValue() {
        return value;
    }

    public BigDecimal getMinValue() {
        return minValue;
    }

    public BigDecimal getMaxValue() {
        return maxValue;
    }
}
