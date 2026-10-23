package com.bibliotheque.member.web;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.bibliotheque.member.domain.Guardian;
import com.bibliotheque.member.domain.MemberContact;
import com.bibliotheque.member.domain.MemberIdentity;
import com.bibliotheque.member.service.MemberCommand;

/** Requêtes du module adhérents (ARCH-adherents §3) ; les messages sont ceux d'UX-adherents. */
final class MemberRequests {

    /** Q-24 : français (10 chiffres commençant par 0) ou international (+ et 8 à 15 chiffres), espaces tolérés. */
    static final String PHONE = "^(?:0(?:\\s*\\d){9}|\\+(?:\\s*\\d){8,15})$";
    static final String PHONE_MESSAGE = "Saisissez un numéro de téléphone valide.";
    static final String POSTAL_CODE = "^\\d{5}$";
    static final String POSTAL_CODE_MESSAGE = "Le code postal doit contenir 5 chiffres.";
    static final String REQUIRED = "Ce champ est obligatoire.";

    private MemberRequests() {
    }

    record GuardianRequest(
            @NotBlank(message = REQUIRED) @Size(max = 100) String lastName,
            @NotBlank(message = REQUIRED) @Size(max = 100) String firstName,
            @NotBlank(message = REQUIRED) @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone) {

        Guardian toGuardian() {
            return new Guardian(lastName.trim(), firstName.trim(), compactPhone(phone));
        }
    }

    record MemberCreateRequest(
            @NotBlank(message = REQUIRED) @Size(max = 100, message = "100 caractères maximum.") String lastName,
            @NotBlank(message = REQUIRED) @Size(max = 100, message = "100 caractères maximum.") String firstName,
            @NotNull(message = REQUIRED) LocalDate birthDate,
            @Valid GuardianRequest guardian,
            @NotBlank(message = REQUIRED) @Email(message = "Saisissez une adresse e-mail valide.") @Size(max = 254)
            String email,
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            @NotBlank(message = REQUIRED) @Size(max = 255) String street,
            @NotBlank(message = REQUIRED) @Pattern(regexp = POSTAL_CODE, message = POSTAL_CODE_MESSAGE) String postalCode,
            @NotBlank(message = REQUIRED) @Size(max = 100) String city,
            @NotBlank(message = REQUIRED) String password,
            boolean confirmDuplicate) {

        MemberCommand toCommand() {
            return command(lastName, firstName, birthDate, guardian, email, phone, street, postalCode, city);
        }
    }

    record MemberUpdateRequest(
            @NotBlank(message = REQUIRED) @Size(max = 100, message = "100 caractères maximum.") String lastName,
            @NotBlank(message = REQUIRED) @Size(max = 100, message = "100 caractères maximum.") String firstName,
            @NotNull(message = REQUIRED) LocalDate birthDate,
            @Valid GuardianRequest guardian,
            @NotBlank(message = REQUIRED) @Email(message = "Saisissez une adresse e-mail valide.") @Size(max = 254)
            String email,
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            @NotBlank(message = REQUIRED) @Size(max = 255) String street,
            @NotBlank(message = REQUIRED) @Pattern(regexp = POSTAL_CODE, message = POSTAL_CODE_MESSAGE) String postalCode,
            @NotBlank(message = REQUIRED) @Size(max = 100) String city,
            @NotNull Integer version) {

        MemberCommand toCommand() {
            return command(lastName, firstName, birthDate, guardian, email, phone, street, postalCode, city);
        }
    }

    record SuspendMemberRequest(
            @NotBlank(message = "Le motif doit contenir entre 3 et 255 caractères.")
            @Size(min = 3, max = 255, message = "Le motif doit contenir entre 3 et 255 caractères.") String reason) {
    }

    /** US-024 : seuls ces champs existent ; toute autre propriété est refusée (AC-024-2). */
    record ContactRequest(
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            @NotBlank(message = REQUIRED) @Size(max = 255) String street,
            @NotBlank(message = REQUIRED) @Pattern(regexp = POSTAL_CODE, message = POSTAL_CODE_MESSAGE) String postalCode,
            @NotBlank(message = REQUIRED) @Size(max = 100) String city,
            @NotNull Integer version) {

        MemberContact toContact() {
            return new MemberContact(compactPhone(phone), street.trim(), postalCode, city.trim());
        }
    }

    private static MemberCommand command(String lastName, String firstName, LocalDate birthDate,
                                         GuardianRequest guardian, String email, String phone, String street,
                                         String postalCode, String city) {
        MemberIdentity identity = new MemberIdentity(lastName.trim(), firstName.trim(), birthDate,
                guardian == null ? null : guardian.toGuardian());
        return new MemberCommand(identity, new MemberContact(compactPhone(phone), street.trim(), postalCode,
                city.trim()), email.trim());
    }

    /** Téléphone stocké sans espaces ; vide → {@code null}. */
    static String compactPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.replaceAll("\\s", "");
    }
}
