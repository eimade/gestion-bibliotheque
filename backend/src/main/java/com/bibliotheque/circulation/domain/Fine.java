package com.bibliotheque.circulation.domain;

import java.math.BigDecimal;
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

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/** Pénalité (ENT-Penalite) : montant figé à la création (RG-36, RG-37), réglée ou remise une seule fois (RG-39). */
@Entity
@Table(name = "fines")
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "loan_id", nullable = false, updatable = false)
    private Long loanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20, updatable = false)
    private FineReason reason;

    @Column(name = "late_days", updatable = false)
    private Integer lateDays;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FineStatus status;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settled_by")
    private Long settledBy;

    @Column(name = "waiver_reason", length = 255)
    private String waiverReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected Fine() {
    }

    private Fine(long memberId, long loanId, FineReason reason, Integer lateDays, BigDecimal amount, Instant now) {
        this.memberId = memberId;
        this.loanId = loanId;
        this.reason = reason;
        this.lateDays = lateDays;
        this.amount = amount;
        this.status = FineStatus.UNPAID;
        this.createdAt = now;
    }

    /** RG-36 : retard de {@code lateDays} jours (≥ 1). */
    public static Fine lateReturn(long memberId, long loanId, int lateDays, BigDecimal amount, Instant now) {
        return new Fine(memberId, loanId, FineReason.LATE_RETURN, lateDays, amount, now);
    }

    /** RG-37 : forfait de perte. */
    public static Fine lostItem(long memberId, long loanId, BigDecimal amount, Instant now) {
        return new Fine(memberId, loanId, FineReason.LOST_ITEM, null, amount, now);
    }

    /** RG-38 : réglée par un encaissement. */
    public void pay(long payment, Instant now, long agentAccountId) {
        requireUnpaid();
        this.status = FineStatus.PAID;
        this.paymentId = payment;
        this.settledAt = now;
        this.settledBy = agentAccountId;
    }

    /** RG-39 : remise gracieuse motivée. */
    public void waive(String motive, Instant now, long adminAccountId) {
        requireUnpaid();
        this.status = FineStatus.WAIVED;
        this.waiverReason = motive;
        this.settledAt = now;
        this.settledBy = adminAccountId;
    }

    private void requireUnpaid() {
        if (status != FineStatus.UNPAID) {
            throw BusinessException.of(ErrorCode.FINE_ALREADY_SETTLED,
                    "Cette pénalité a déjà été réglée ou remise.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getLoanId() {
        return loanId;
    }

    public FineReason getReason() {
        return reason;
    }

    public Integer getLateDays() {
        return lateDays;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public FineStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Integer getVersion() {
        return version;
    }
}
