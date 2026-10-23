package com.bibliotheque.circulation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.Column;
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
 * Réservation d'une notice (ENT-Reservation, RG-29). L'exemplaire n'est attribué qu'au passage à
 * {@code READY} (RG-32) ; il reste renseigné une fois la réservation close.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    /** Réservations « actives » (SPEC-reservations §4). */
    public static final Set<ReservationStatus> ACTIVE = EnumSet.of(ReservationStatus.PENDING, ReservationStatus.READY);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false, updatable = false)
    private Long bookId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "reserved_at", nullable = false, updatable = false)
    private Instant reservedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "copy_id")
    private Long copyId;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "pickup_deadline")
    private LocalDate pickupDeadline;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "notification_status", length = 20)
    private String notificationStatus;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "closed_by")
    private Long closedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected Reservation() {
    }

    /** US-032 : réservation en attente, par l'adhérent ou par un agent ({@code createdBy}). */
    public Reservation(long bookId, long memberId, Instant reservedAt, long createdBy) {
        this.bookId = bookId;
        this.memberId = memberId;
        this.reservedAt = reservedAt;
        this.createdBy = createdBy;
        this.status = ReservationStatus.PENDING;
    }

    public boolean isActive() {
        return ACTIVE.contains(status);
    }

    /** RG-32, RG-33 : un exemplaire est mis de côté jusqu'à la date limite de retrait. */
    public void makeReady(long allocatedCopyId, Instant now, LocalDate deadline) {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Seule une réservation en attente peut être mise à disposition");
        }
        this.status = ReservationStatus.READY;
        this.copyId = allocatedCopyId;
        this.readyAt = now;
        this.pickupDeadline = deadline;
    }

    /** RG-24 : l'adhérent emprunte l'exemplaire mis de côté. */
    public void fulfil(Instant now, long agentAccountId) {
        if (status != ReservationStatus.READY) {
            throw new IllegalStateException("Seule une réservation prête peut être honorée");
        }
        close(ReservationStatus.FULFILLED, now, agentAccountId);
    }

    /** RG-34 : annulation par l'adhérent ou le personnel. */
    public void cancel(Instant now, long actorAccountId) {
        if (!isActive()) {
            throw BusinessException.of(ErrorCode.RESERVATION_CLOSED, "Cette réservation est déjà close.");
        }
        close(ReservationStatus.CANCELLED, now, actorAccountId);
    }

    /** RG-33 : réservation prête non retirée à temps (traitement « Système »). */
    public void expire(Instant now) {
        if (status != ReservationStatus.READY) {
            throw new IllegalStateException("Seule une réservation prête peut expirer");
        }
        this.status = ReservationStatus.EXPIRED;
        this.closedAt = now;
    }

    /** ADR-006 §3 : la date limite de retrait est dépassée. */
    public boolean isPickupOverdue(LocalDate today) {
        return status == ReservationStatus.READY && pickupDeadline.isBefore(today);
    }

    private void close(ReservationStatus closedStatus, Instant now, long actorAccountId) {
        this.status = closedStatus;
        this.closedAt = now;
        this.closedBy = actorAccountId;
    }

    public Long getId() {
        return id;
    }

    public Long getBookId() {
        return bookId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Long getCopyId() {
        return copyId;
    }

    public LocalDate getPickupDeadline() {
        return pickupDeadline;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getNotificationStatus() {
        return notificationStatus;
    }
}
