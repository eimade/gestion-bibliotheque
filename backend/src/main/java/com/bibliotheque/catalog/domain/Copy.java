package com.bibliotheque.catalog.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/**
 * Exemplaire (ENT-Exemplaire). Jamais supprimé : retiré du fonds avec un motif (RG-10). Les transitions
 * de circulation (emprunt, retour, perte) relèvent du module circulation.
 */
@Entity
@Table(name = "copies")
public class Copy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "copy_number", nullable = false, updatable = false, columnDefinition = "char(8)")
    private String copyNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false, updatable = false)
    private Book book;

    @Column(name = "shelf_mark", nullable = false, length = 30)
    private String shelfMark;

    @Enumerated(EnumType.STRING)
    @Column(name = "physical_condition", nullable = false, length = 20)
    private PhysicalCondition physicalCondition;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CopyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_reason", length = 20)
    private WithdrawalReason withdrawalReason;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected Copy() {
    }

    /** Nouvel exemplaire, disponible (US-012). */
    public Copy(String copyNumber, Book book, String shelfMark, PhysicalCondition physicalCondition,
                LocalDate acquisitionDate) {
        this.copyNumber = copyNumber;
        this.book = book;
        this.shelfMark = shelfMark;
        this.physicalCondition = physicalCondition;
        this.acquisitionDate = acquisitionDate;
        this.status = CopyStatus.AVAILABLE;
    }

    /** US-013 : cote et état physique ; un exemplaire retiré n'est plus modifiable (AC-013-5). */
    public void update(String newShelfMark, PhysicalCondition newCondition) {
        requireNotWithdrawn();
        this.shelfMark = newShelfMark;
        this.physicalCondition = newCondition;
    }

    /** RG-10 : retrait définitif, seulement depuis {@code AVAILABLE} ou {@code LOST}. */
    public void withdraw(WithdrawalReason reason, Instant now) {
        requireNotWithdrawn();
        if (status != CopyStatus.AVAILABLE && status != CopyStatus.LOST) {
            throw BusinessException.of(ErrorCode.COPY_IN_CIRCULATION,
                    "Retrait impossible : l'exemplaire est en circulation.");
        }
        this.status = CopyStatus.WITHDRAWN;
        this.withdrawalReason = reason;
        this.withdrawnAt = now;
    }

    /**
     * Transition de circulation décidée par le module circulation (INDEX §5) : emprunt, retour, mise de côté,
     * perte, exemplaire retrouvé. Un exemplaire retiré n'entre plus jamais en circulation.
     */
    public void changeCirculationStatus(CopyStatus target) {
        requireNotWithdrawn();
        if (target == CopyStatus.WITHDRAWN) {
            throw new IllegalArgumentException("Le retrait passe par withdraw()");
        }
        this.status = target;
    }

    private void requireNotWithdrawn() {
        if (status == CopyStatus.WITHDRAWN) {
            throw BusinessException.of(ErrorCode.COPY_WITHDRAWN,
                    "Cet exemplaire a été retiré du fonds et ne peut plus être modifié.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getCopyNumber() {
        return copyNumber;
    }

    public Book getBook() {
        return book;
    }

    public String getShelfMark() {
        return shelfMark;
    }

    public PhysicalCondition getPhysicalCondition() {
        return physicalCondition;
    }

    public LocalDate getAcquisitionDate() {
        return acquisitionDate;
    }

    public CopyStatus getStatus() {
        return status;
    }

    public WithdrawalReason getWithdrawalReason() {
        return withdrawalReason;
    }

    public Integer getVersion() {
        return version;
    }
}
