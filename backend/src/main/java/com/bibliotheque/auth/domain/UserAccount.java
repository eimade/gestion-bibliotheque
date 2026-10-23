package com.bibliotheque.auth.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Compte utilisateur (ENT-Compte). Les transitions d'état sont portées par l'entité pour garder
 * les invariants de la SPEC (RG-02, RG-05) au même endroit.
 */
@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "password_temporary", nullable = false)
    private boolean passwordTemporary;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected UserAccount() {
    }

    /** Compte du personnel avec mot de passe temporaire (US-004, RG-05). */
    public static UserAccount staff(String email, String lastName, String firstName, AccountRole role,
                                    String passwordHash, Instant now) {
        if (!role.isStaff()) {
            throw new IllegalArgumentException("Un compte du personnel ne peut pas avoir le rôle " + role);
        }
        UserAccount account = new UserAccount();
        account.email = email;
        account.lastName = lastName;
        account.firstName = firstName;
        account.role = role;
        account.status = AccountStatus.ACTIVE;
        account.passwordHash = passwordHash;
        account.passwordTemporary = true;
        account.passwordChangedAt = now;
        return account;
    }

    /** Compte d'un adhérent, créé à l'inscription avec un mot de passe temporaire (AC-016-1, RG-04, RG-05). */
    public static UserAccount member(String email, long memberId, String passwordHash, Instant now) {
        UserAccount account = new UserAccount();
        account.email = email;
        account.memberId = memberId;
        account.role = AccountRole.MEMBER;
        account.status = AccountStatus.ACTIVE;
        account.passwordHash = passwordHash;
        account.passwordTemporary = true;
        account.passwordChangedAt = now;
        return account;
    }

    /** AC-019-1 : le nouvel e-mail devient l'identifiant de connexion. */
    public void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    /** RG-18 : à l'archivage de l'adhérent, l'e-mail est effacé et le compte désactivé. */
    public void anonymizeForArchive() {
        if (role != AccountRole.MEMBER) {
            throw new IllegalStateException("Seul un compte adhérent peut être anonymisé");
        }
        this.email = null;
        disable();
    }

    /** Lève un verrou échu : le compte redevient actif avec un compteur remis à zéro (AC-001-6). */
    public void releaseExpiredLock(Instant now) {
        if (status == AccountStatus.LOCKED && lockedUntil != null && !lockedUntil.isAfter(now)) {
            status = AccountStatus.ACTIVE;
            lockedUntil = null;
            failedLoginCount = 0;
        }
    }

    /**
     * Enregistre un échec de connexion (RG-02).
     *
     * @return {@code true} si cet échec verrouille le compte
     */
    public boolean registerFailedLogin(Instant now, int maxFailedAttempts, Duration lockDuration) {
        if (status != AccountStatus.ACTIVE) {
            return false;
        }
        failedLoginCount++;
        if (failedLoginCount >= maxFailedAttempts) {
            status = AccountStatus.LOCKED;
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lastLoginAt = now;
    }

    public void changePassword(String newPasswordHash, boolean temporary, Instant now) {
        passwordHash = newPasswordHash;
        passwordTemporary = temporary;
        passwordChangedAt = now;
    }

    /** Débloque le compte après une réinitialisation par le personnel (AC-006-1). */
    public void unlock() {
        if (status == AccountStatus.LOCKED) {
            status = AccountStatus.ACTIVE;
        }
        lockedUntil = null;
        failedLoginCount = 0;
    }

    public void disable() {
        status = AccountStatus.DISABLED;
        lockedUntil = null;
    }

    /** AC-005-2 : réactivation avec compteur d'échecs à zéro. */
    public void reactivate() {
        status = AccountStatus.ACTIVE;
        lockedUntil = null;
        failedLoginCount = 0;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountRole getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public Long getMemberId() {
        return memberId;
    }

    public boolean isPasswordTemporary() {
        return passwordTemporary;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Integer getVersion() {
        return version;
    }
}
