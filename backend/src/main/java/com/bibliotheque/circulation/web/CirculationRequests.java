package com.bibliotheque.circulation.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.bibliotheque.circulation.domain.PaymentMethod;

/** Requêtes de la circulation (ARCH-circulation §3) ; messages exacts d'UX-emprunts et d'UX-penalites. */
final class CirculationRequests {

    static final String MEMBER_NUMBER = "^A\\d{6}$";
    static final String MEMBER_NUMBER_MESSAGE = "Numéro d'adhérent invalide (format attendu : A000123).";
    static final String COPY_NUMBER = "^\\d{8}$";
    static final String COPY_NUMBER_MESSAGE = "Numéro d'exemplaire invalide (8 chiffres attendus).";

    private CirculationRequests() {
    }

    record LoanRequest(
            @NotBlank(message = MEMBER_NUMBER_MESSAGE) @Pattern(regexp = MEMBER_NUMBER, message = MEMBER_NUMBER_MESSAGE)
            String memberNumber,
            @NotBlank(message = COPY_NUMBER_MESSAGE) @Pattern(regexp = COPY_NUMBER, message = COPY_NUMBER_MESSAGE)
            String copyNumber) {
    }

    record ReturnRequest(
            @NotBlank(message = COPY_NUMBER_MESSAGE) @Pattern(regexp = COPY_NUMBER, message = COPY_NUMBER_MESSAGE)
            String copyNumber) {
    }

    record StaffReservationRequest(
            @NotNull Long bookId,
            @NotBlank(message = MEMBER_NUMBER_MESSAGE) @Pattern(regexp = MEMBER_NUMBER, message = MEMBER_NUMBER_MESSAGE)
            String memberNumber) {
    }

    record MyReservationRequest(@NotNull Long bookId) {
    }

    /** RG-38 : sélection non vide, mode obligatoire, montant en euros à 2 décimales. */
    record PaymentRequest(
            @NotBlank(message = MEMBER_NUMBER_MESSAGE) @Pattern(regexp = MEMBER_NUMBER, message = MEMBER_NUMBER_MESSAGE)
            String memberNumber,
            @NotEmpty(message = "Sélectionnez au moins une pénalité.") List<@NotNull Long> fineIds,
            @NotNull(message = "Sélectionnez un mode de paiement.") PaymentMethod method,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount) {
    }

    record WaiverRequest(
            @NotBlank(message = "Le motif doit contenir entre 3 et 255 caractères.")
            @Size(min = 3, max = 255, message = "Le motif doit contenir entre 3 et 255 caractères.") String reason) {
    }
}
