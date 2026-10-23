package com.bibliotheque.common.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entrée immuable du journal d'audit (NFR-08) : aucun accesseur de modification. */
@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** {@code null} pour un traitement planifié (« Système ») ou une tentative de connexion anonyme. */
    @Column(name = "actor_account_id", updatable = false)
    private Long actorAccountId;

    @Column(name = "actor_label", nullable = false, updatable = false, length = 150)
    private String actorLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 30)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, updatable = false, length = 30)
    private AuditObjectType objectType;

    @Column(name = "object_ref", nullable = false, updatable = false, length = 60)
    private String objectRef;

    /** Valeurs avant/après en JSON, jamais de secret. */
    @Column(name = "details", updatable = false, columnDefinition = "longtext")
    private String details;

    protected AuditEntry() {
    }

    AuditEntry(Instant occurredAt, Long actorAccountId, String actorLabel, AuditAction action,
               AuditObjectType objectType, String objectRef, String details) {
        this.occurredAt = occurredAt;
        this.actorAccountId = actorAccountId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.objectType = objectType;
        this.objectRef = objectRef;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getActorAccountId() {
        return actorAccountId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditObjectType getObjectType() {
        return objectType;
    }

    public String getObjectRef() {
        return objectRef;
    }

    public String getDetails() {
        return details;
    }
}
