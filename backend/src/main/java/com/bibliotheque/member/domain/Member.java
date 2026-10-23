package com.bibliotheque.member.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/**
 * Adhérent (ENT-Adherent). Son e-mail est porté par son compte (RG-14). Après archivage, seuls le
 * numéro, l'année de naissance et le code postal subsistent (RG-18).
 */
@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_number", nullable = false, updatable = false, columnDefinition = "char(7)")
    private String memberNumber;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_year", nullable = false)
    private short birthYear;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "street", length = 255)
    private String street;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "city", length = 100)
    private String city;

    @Embedded
    private Guardian guardian;

    @Column(name = "registration_date", nullable = false, updatable = false)
    private LocalDate registrationDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "suspension_reason", length = 255)
    private String suspensionReason;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected Member() {
    }

    /** Inscription (US-016) : adhérent actif, adhésion valable jusqu'à {@code expiryDate} (RG-15). */
    public Member(String memberNumber, MemberIdentity identity, MemberContact contact, LocalDate registrationDate,
                  LocalDate expiryDate) {
        this.memberNumber = memberNumber;
        this.registrationDate = registrationDate;
        this.expiryDate = expiryDate;
        this.status = MemberStatus.ACTIVE;
        applyIdentity(identity);
        applyContact(contact);
    }

    /** Statut à la date du jour : un adhérent actif dont l'adhésion est échue est expiré (RG-16, ADR-006 §3). */
    public MemberStatus effectiveStatus(LocalDate today) {
        return status == MemberStatus.ACTIVE && expiryDate.isBefore(today) ? MemberStatus.EXPIRED : status;
    }

    /** US-019 : identité modifiable par le personnel ; numéro et date d'inscription ne le sont pas. */
    public void updateIdentity(MemberIdentity identity) {
        requireNotArchived("Un adhérent archivé ne peut plus être modifié.");
        applyIdentity(identity);
    }

    /** US-019, US-024 : coordonnées postales et téléphone. */
    public void updateContact(MemberContact contact) {
        requireNotArchived("Un adhérent archivé ne peut plus être modifié.");
        applyContact(contact);
    }

    /** RG-15 : nouvelle échéance calculée par la politique d'adhésion ; l'adhérent redevient actif. */
    public void renew(LocalDate newExpiryDate) {
        this.expiryDate = newExpiryDate;
        this.status = MemberStatus.ACTIVE;
    }

    /** RG-17 : motif obligatoire ; la date d'expiration n'est pas modifiée. */
    public void suspend(String reason) {
        requireNotArchived("Un adhérent archivé ne peut pas être suspendu.");
        this.status = MemberStatus.SUSPENDED;
        this.suspensionReason = reason;
    }

    /** AC-021-2, AC-021-3 : redevient actif, ou expiré si l'adhésion est échue. */
    public void reactivate(LocalDate today) {
        if (status != MemberStatus.SUSPENDED) {
            return;
        }
        this.suspensionReason = null;
        this.status = expiryDate.isBefore(today) ? MemberStatus.EXPIRED : MemberStatus.ACTIVE;
    }

    /** RG-16 : passage automatique à l'état expiré. */
    public void expire() {
        if (status == MemberStatus.ACTIVE) {
            status = MemberStatus.EXPIRED;
        }
    }

    /** RG-18 : effacement irréversible des données personnelles. */
    public void archive(Instant now) {
        this.status = MemberStatus.ARCHIVED;
        this.archivedAt = now;
        this.suspensionReason = null;
        this.lastName = null;
        this.firstName = null;
        this.birthDate = null;
        this.phone = null;
        this.street = null;
        this.city = null;
        this.guardian = null;
    }

    private void applyIdentity(MemberIdentity identity) {
        this.lastName = identity.lastName();
        this.firstName = identity.firstName();
        this.birthDate = identity.birthDate();
        this.birthYear = (short) identity.birthDate().getYear();
        this.guardian = identity.guardian();
    }

    private void applyContact(MemberContact contact) {
        this.phone = contact.phone();
        this.street = contact.street();
        this.postalCode = contact.postalCode();
        this.city = contact.city();
    }

    private void requireNotArchived(String message) {
        if (status == MemberStatus.ARCHIVED) {
            throw BusinessException.of(ErrorCode.MEMBER_ARCHIVED, message);
        }
    }

    public Long getId() {
        return id;
    }

    public String getMemberNumber() {
        return memberNumber;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getPhone() {
        return phone;
    }

    public String getStreet() {
        return street;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public Guardian getGuardian() {
        return guardian;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    public Integer getVersion() {
        return version;
    }
}
