package com.bibliotheque.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String password) {

    /** Ne jamais exposer le mot de passe dans les journaux. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
