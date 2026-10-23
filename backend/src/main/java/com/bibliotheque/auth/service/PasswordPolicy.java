package com.bibliotheque.auth.service;

import org.springframework.stereotype.Component;

import com.bibliotheque.common.config.AppProperties;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/** RG-01 : longueur minimale, au moins une majuscule, une minuscule et un chiffre. */
@Component
public class PasswordPolicy {

    private final int minLength;

    public PasswordPolicy(AppProperties properties) {
        this.minLength = properties.security().password().minLength();
    }

    public boolean isCompliant(String password) {
        return password != null
                && password.length() >= minLength
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit);
    }

    /** @param field champ concerné, renvoyé au client dans la propriété {@code field} */
    public void check(String password, String field) {
        if (!isCompliant(password)) {
            throw BusinessException.of(ErrorCode.PASSWORD_POLICY_VIOLATION,
                            "Le mot de passe doit contenir au moins " + minLength
                                    + " caractères, dont une majuscule, une minuscule et un chiffre.")
                    .withProperty("field", field);
        }
    }
}
