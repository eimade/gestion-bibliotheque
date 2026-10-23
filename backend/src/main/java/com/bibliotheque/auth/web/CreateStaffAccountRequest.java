package com.bibliotheque.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.service.CreateStaffAccountCommand;

public record CreateStaffAccountRequest(
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull AccountRole role,
        @NotBlank @Size(max = 128) String initialPassword) {

    CreateStaffAccountCommand toCommand() {
        return new CreateStaffAccountCommand(lastName, firstName, email, role, initialPassword);
    }

    @Override
    public String toString() {
        return "CreateStaffAccountRequest[email=" + email + ", role=" + role + "]";
    }
}
