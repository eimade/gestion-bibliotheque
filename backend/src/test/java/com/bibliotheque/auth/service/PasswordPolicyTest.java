package com.bibliotheque.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.support.TestFixtures;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(TestFixtures.properties());

    @Test
    void accepteUnMotDePasseDe12CaracteresAvecMajusculeMinusculeEtChiffre() {
        assertThat(policy.isCompliant("Abcdefghijk1")).isTrue();
    }

    @ParameterizedTest(name = "refuse « {0} »")
    @NullAndEmptySource
    @ValueSource(strings = {
            "Abcdefghij1",   // 11 caractères : limite RG-01 − 1
            "abcdefghijk1",  // sans majuscule
            "ABCDEFGHIJK1",  // sans minuscule
            "Abcdefghijkl"   // sans chiffre
    })
    void refuseUnMotDePasseNonConforme(String password) {
        assertThat(policy.isCompliant(password)).isFalse();
    }

    @Test
    void checkLeveUneErreurAvecLeChampConcerne() {
        assertThatThrownBy(() -> policy.check("court", "newPassword"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);
                    assertThat(ex.properties()).containsEntry("field", "newPassword");
                    assertThat(ex.getMessage()).contains("12 caractères");
                });
    }
}
