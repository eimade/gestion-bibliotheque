package com.bibliotheque.common.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SettingsServiceTest {

    private final SettingRepository repository = mock(SettingRepository.class);
    private final SettingsService service = new SettingsService(repository);

    @Test
    void litUnParametreEntier() {
        when(repository.findById(SettingCode.LOAN_DURATION_DAYS))
                .thenReturn(Optional.of(setting(SettingCode.LOAN_DURATION_DAYS, SettingValueType.INTEGER, "21.00")));

        assertThat(service.getInt(SettingCode.LOAN_DURATION_DAYS)).isEqualTo(21);
    }

    @Test
    void litUnMontantAvecDeuxDecimales() {
        when(repository.findById(SettingCode.FINE_DAILY_RATE))
                .thenReturn(Optional.of(setting(SettingCode.FINE_DAILY_RATE, SettingValueType.MONEY, "0.2")));

        BigDecimal amount = service.getAmount(SettingCode.FINE_DAILY_RATE);

        assertThat(amount).isEqualByComparingTo("0.20");
        assertThat(amount.scale()).isEqualTo(2);
    }

    @Test
    void refuseUnTypeIncoherent() {
        when(repository.findById(SettingCode.FINE_DAILY_RATE))
                .thenReturn(Optional.of(setting(SettingCode.FINE_DAILY_RATE, SettingValueType.MONEY, "0.20")));

        assertThatThrownBy(() -> service.getInt(SettingCode.FINE_DAILY_RATE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void echoueSiLeParametreEstAbsent() {
        assertThatThrownBy(() -> service.getInt(SettingCode.LOAN_MAX_ACTIVE)).isInstanceOf(IllegalStateException.class);
    }

    private static Setting setting(SettingCode code, SettingValueType type, String value) {
        Setting setting = new Setting();
        ReflectionTestUtils.setField(setting, "code", code);
        ReflectionTestUtils.setField(setting, "valueType", type);
        ReflectionTestUtils.setField(setting, "value", new BigDecimal(value));
        return setting;
    }
}
