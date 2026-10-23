package com.bibliotheque.circulation.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.service.CopyCirculationService;
import com.bibliotheque.catalog.service.CopyCirculationService.CopySnapshot;
import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.ReservationStatus;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.circulation.repository.ReservationRepository;
import com.bibliotheque.circulation.service.CirculationViews.CopyLoanHistoryItem;
import com.bibliotheque.circulation.service.CirculationViews.HistoryLoan;
import com.bibliotheque.circulation.service.CirculationViews.MemberBrief;
import com.bibliotheque.circulation.service.CirculationViews.MemberContactBrief;
import com.bibliotheque.circulation.service.CirculationViews.MemberLoan;
import com.bibliotheque.circulation.service.CirculationViews.OverdueLoan;
import com.bibliotheque.circulation.service.CirculationViews.RenewalView;
import com.bibliotheque.common.config.AppProperties;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.member.service.MemberCirculation.Eligibility;
import com.bibliotheque.member.service.MemberLookup;
import com.bibliotheque.member.service.MemberViews.MemberRef;

/** Lectures des emprunts : situation d'un adhérent (ECR-18, ECR-22), retards (US-028), historique (US-031). */
@Service
public class CirculationReadService {

    /** Tris de la liste des retards (UX-emprunts ECR-26). */
    public enum OverdueSort {
        OVERDUE_DAYS,
        DUE_DATE,
        MEMBER_NAME
    }

    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final CopyCirculationService copies;
    private final MemberLookup members;
    private final EligibilityService eligibility;
    private final LoanRenewalPolicy renewalPolicy;
    private final FineCalculator calculator;
    private final BusinessTime time;
    private final AppProperties properties;

    public CirculationReadService(LoanRepository loans, ReservationRepository reservations,
                                  CopyCirculationService copies, MemberLookup members, EligibilityService eligibility,
                                  LoanRenewalPolicy renewalPolicy, FineCalculator calculator, BusinessTime time,
                                  AppProperties properties) {
        this.loans = loans;
        this.reservations = reservations;
        this.copies = copies;
        this.members = members;
        this.eligibility = eligibility;
        this.renewalPolicy = renewalPolicy;
        this.calculator = calculator;
        this.time = time;
        this.properties = properties;
    }

    /** Emprunts en cours, échéance la plus proche d'abord, avec possibilité de prolonger (AC-023-1). */
    @Transactional(readOnly = true)
    public List<MemberLoan> memberLoans(long memberId) {
        LocalDate today = time.today();
        List<Loan> ongoing = loans.findByMemberIdAndStatusInOrderByDueDateAscIdAsc(memberId, Loan.ONGOING);
        if (ongoing.isEmpty()) {
            return List.of();
        }
        MemberRef member = members.require(memberId);
        Eligibility memberEligibility = eligibility.evaluate(memberId, member.status(), false);
        Map<Long, CopySnapshot> copiesById = copies.describe(ongoing.stream().map(Loan::getCopyId).toList());
        Map<Long, Boolean> reservedBooks = new HashMap<>();
        return ongoing.stream().map(loan -> {
            CopySnapshot copy = copiesById.get(loan.getCopyId());
            boolean reserved = reservedBooks.computeIfAbsent(copy.bookId(),
                    bookId -> reservations.existsByBookIdAndStatus(bookId, ReservationStatus.PENDING));
            return memberLoan(loan, copy, today, calculator,
                    renewalPolicy.evaluate(loan, memberEligibility, reserved, today));
        }).toList();
    }

    /** RG-19 : emprunts rendus au cours des 4 derniers mois (AC-023-2). */
    @Transactional(readOnly = true)
    public Page<HistoryLoan> history(long memberId, Pageable pageable) {
        LocalDate since = time.today().minus(properties.privacy().loanHistoryRetention());
        Page<Loan> page = loans.findHistory(memberId, since, pageable);
        Map<Long, CopySnapshot> copiesById = copies.describe(page.map(Loan::getCopyId).getContent());
        return page.map(loan -> {
            CopySnapshot copy = copiesById.get(loan.getCopyId());
            return new HistoryLoan(loan.getId(), copy.copyNumber(), copy.title(), loan.getLoanDate(),
                    loan.getDueDate(), loan.getReturnDate());
        });
    }

    /** US-028 : retards d'au moins {@code minDays} jours, avec contacts et pénalité provisoire. */
    @Transactional(readOnly = true)
    public Page<OverdueLoan> overdue(int minDays, OverdueSort sort, int page, int size) {
        LocalDate today = time.today();
        LocalDate dueOnOrBefore = today.minusDays(Math.max(1, minDays));
        Page<Loan> loansPage = switch (sort) {
            case MEMBER_NAME -> loans.findOverdueByMemberName(Loan.ONGOING, dueOnOrBefore, PageRequest.of(page, size));
            case DUE_DATE -> loans.findOverdue(Loan.ONGOING, dueOnOrBefore,
                    PageRequest.of(page, size, Sort.by("dueDate").descending().and(Sort.by("id"))));
            case OVERDUE_DAYS -> loans.findOverdue(Loan.ONGOING, dueOnOrBefore,
                    PageRequest.of(page, size, Sort.by("dueDate").ascending().and(Sort.by("id"))));
        };
        Map<Long, CopySnapshot> copiesById = copies.describe(loansPage.map(Loan::getCopyId).getContent());
        Map<Long, MemberRef> membersById = members.refs(loansPage.map(Loan::getMemberId).getContent());
        return loansPage.map(loan -> {
            CopySnapshot copy = copiesById.get(loan.getCopyId());
            int days = loan.overdueDays(today);
            return new OverdueLoan(loan.getId(), copy.copyNumber(), copy.title(),
                    MemberContactBrief.of(membersById.get(loan.getMemberId())), loan.getDueDate(), days,
                    calculator.lateFine(days));
        });
    }

    /** US-031 : emprunteur masqué une fois l'emprunt anonymisé (AC-031-2). */
    @Transactional(readOnly = true)
    public Page<CopyLoanHistoryItem> copyHistory(String copyNumber, Pageable pageable) {
        CopySnapshot copy = copies.findByNumber(copyNumber)
                .orElseThrow(() -> BusinessException.of(ErrorCode.COPY_NOT_FOUND, "Exemplaire inconnu."));
        LocalDate today = time.today();
        Page<Loan> page = loans.findByCopyIdOrderByLoanDateDescIdDesc(copy.id(), pageable);
        Map<Long, MemberRef> membersById = members.refs(page.getContent().stream()
                .map(Loan::getMemberId).filter(Objects::nonNull).distinct().toList());
        return page.map(loan -> new CopyLoanHistoryItem(loan.getLoanDate(), loan.getDueDate(), loan.getReturnDate(),
                loan.effectiveStatus(today),
                loan.getMemberId() == null ? null : MemberBrief.of(membersById.get(loan.getMemberId()))));
    }

    static MemberLoan memberLoan(Loan loan, CopySnapshot copy, LocalDate today, FineCalculator calculator,
                                 RenewalView renewal) {
        int days = loan.overdueDays(today);
        return new MemberLoan(loan.getId(), copy.copyNumber(), copy.bookId(), copy.title(), loan.getLoanDate(),
                loan.getDueDate(), loan.effectiveStatus(today), loan.getRenewalCount(), days,
                calculator.lateFine(days), renewal);
    }
}
