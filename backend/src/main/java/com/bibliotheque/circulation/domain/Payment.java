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

/** Encaissement au guichet (RG-38) : une opération règle une ou plusieurs pénalités ; jamais modifié. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10, updatable = false)
    private PaymentMethod method;

    @Column(name = "collected_by", nullable = false, updatable = false)
    private Long collectedBy;

    @Column(name = "collected_at", nullable = false, updatable = false)
    private Instant collectedAt;

    protected Payment() {
    }

    public Payment(long memberId, BigDecimal amount, PaymentMethod method, long collectedBy, Instant collectedAt) {
        this.memberId = memberId;
        this.amount = amount;
        this.method = method;
        this.collectedBy = collectedBy;
        this.collectedAt = collectedAt;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }
}
