package com.bibliotheque.circulation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * Emprunt (ENT-Emprunt). L'exemplaire et l'adhérent sont référencés par identifiant (ADR-001 §4) ;
 * l'adhérent est effacé 4 mois après le retour (RG-19).
 */
@Entity
@Table(name = "loans")
public class Loan {

    /** Emprunts « en cours » (SPEC-emprunts §4). */
    public static final Set<LoanStatus> ONGOING = EnumSet.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "copy_id", nullable = false, updatable = false)
    private Long copyId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "loan_date", nullable = false, updatable = false)
    private LocalDate loanDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(name = "lost_declared_date")
    private LocalDate lostDeclaredDate;

    @Column(name = "renewal_count", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer renewalCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LoanStatus status;

    @Column(name = "loaned_by", nullable = false, updatable = false)
    private Long loanedBy;

    @Column(name = "returned_by")
    private Long returnedBy;

    @Column(name = "member_unlinked_at")
    private Instant memberUnlinkedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected Loan() {
    }

    /** US-025 : emprunt en cours, échéance calculée par l'appelant (RG-21). */
    public Loan(long copyId, long memberId, LocalDate loanDate, LocalDate dueDate, long loanedBy) {
        this.copyId = copyId;
        this.memberId = memberId;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.loanedBy = loanedBy;
        this.renewalCount = 0;
        this.status = LoanStatus.ACTIVE;
    }

    public boolean isOngoing() {
        return ONGOING.contains(status);
    }

    /** RG-27, ADR-006 §3 : un emprunt actif dont l'échéance est passée est en retard, même avant le traitement. */
    public LoanStatus effectiveStatus(LocalDate today) {
        return status == LoanStatus.ACTIVE && dueDate.isBefore(today) ? LoanStatus.OVERDUE : status;
    }

    /** RG-27 : jours de retard à une date (0 si l'échéance n'est pas dépassée). */
    public int overdueDays(LocalDate asOf) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(dueDate, asOf));
    }

    /** RG-25 : nouvelle échéance ; les conditions sont vérifiées par la politique de prolongation. */
    public void renew(LocalDate newDueDate) {
        requireOngoing();
        this.dueDate = newDueDate;
        this.renewalCount = renewalCount + 1;
    }

    /** RG-27 : bascule automatique. */
    public void markOverdue() {
        if (status == LoanStatus.ACTIVE) {
            status = LoanStatus.OVERDUE;
        }
    }

    /** RG-26 : retour à la date du jour, quel que soit le statut de l'adhérent. */
    public void giveBack(LocalDate today, long agentAccountId) {
        requireOngoing();
        this.status = LoanStatus.RETURNED;
        this.returnDate = today;
        this.returnedBy = agentAccountId;
    }

    /** RG-28 : perte déclarée. */
    public void declareLost(LocalDate today) {
        requireOngoing();
        this.status = LoanStatus.LOST;
        this.lostDeclaredDate = today;
    }

    /** RG-19 : effacement de l'emprunteur d'un emprunt rendu. */
    public void unlinkMember(Instant now) {
        if (status != LoanStatus.RETURNED) {
            throw new IllegalStateException("Seul un emprunt rendu peut être anonymisé");
        }
        this.memberId = null;
        this.memberUnlinkedAt = now;
    }

    private void requireOngoing() {
        if (!isOngoing()) {
            throw BusinessException.of(ErrorCode.LOAN_NOT_ACTIVE, "Cet emprunt n'est plus en cours.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCopyId() {
        return copyId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LocalDate getLoanDate() {
        return loanDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public int getRenewalCount() {
        return renewalCount;
    }

    public LoanStatus getStatus() {
        return status;
    }
}
