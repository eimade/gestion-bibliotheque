package com.bibliotheque.circulation.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.config.AppProperties;
import com.bibliotheque.common.config.BusinessTime;

/** Traitements planifiés des emprunts (ADR-006) : idempotents, par lots de 500, auteur « Système ». */
@Service
public class LoanMaintenanceService {

    static final int BATCH_SIZE = 500;

    private final LoanRepository loans;
    private final CirculationAudit audit;
    private final BusinessTime time;
    private final AppProperties properties;

    public LoanMaintenanceService(LoanRepository loans, CirculationAudit audit, BusinessTime time,
                                  AppProperties properties) {
        this.loans = loans;
        this.audit = audit;
        this.time = time;
        this.properties = properties;
    }

    /** RG-27 : passage en retard le lendemain de l'échéance. */
    @Transactional
    public int markNextOverdueBatch() {
        List<Loan> batch = loans.findActiveDueBefore(time.today(), PageRequest.of(0, BATCH_SIZE));
        for (Loan loan : batch) {
            loan.markOverdue();
            audit.log(null, AuditAction.STATE_CHANGE, AuditObjectType.LOAN, loan.getId(),
                    Map.of("from", "ACTIVE", "to", "OVERDUE", "dueDate", loan.getDueDate().toString()));
        }
        loans.flush();
        return batch.size();
    }

    /** RG-19 (D2) : effacement de l'emprunteur des emprunts rendus depuis 4 mois sans pénalité impayée. */
    @Transactional
    public int anonymizeNextBatch() {
        LocalDate limit = time.today().minus(properties.privacy().loanHistoryRetention());
        List<Loan> batch = loans.findToAnonymize(limit, PageRequest.of(0, BATCH_SIZE));
        batch.forEach(loan -> loan.unlinkMember(time.now()));
        loans.flush();
        return batch.size();
    }
}
