package com.bibliotheque.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(@NotBlank @Size(max = 128) String newPassword) {

    @Override
    public String toString() {
        return "PasswordResetRequest[***]";
    }
}
