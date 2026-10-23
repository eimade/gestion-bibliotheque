package com.bibliotheque.circulation.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.service.CopyCirculationService;
import com.bibliotheque.catalog.service.CopyCirculationService.CopySnapshot;
import com.bibliotheque.circulation.domain.Fine;
import com.bibliotheque.circulation.domain.FineStatus;
import com.bibliotheque.circulation.domain.Payment;
import com.bibliotheque.circulation.domain.PaymentMethod;
import com.bibliotheque.circulation.repository.FineRepository;
import com.bibliotheque.circulation.repository.FineRepository.Debtor;
import com.bibliotheque.circulation.repository.LoanRepository;
import com.bibliotheque.circulation.repository.PaymentRepository;
import com.bibliotheque.circulation.service.CirculationViews.DebtorList;
import com.bibliotheque.circulation.service.CirculationViews.DebtorView;
import com.bibliotheque.circulation.service.CirculationViews.FineView;
import com.bibliotheque.circulation.service.CirculationViews.MemberFines;
import com.bibliotheque.circulation.service.CirculationViews.PaymentResult;
import com.bibliotheque.circulation.service.CirculationViews.WaiverResult;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.web.PageResponse;
import com.bibliotheque.member.service.MemberLookup;
import com.bibliotheque.member.service.MemberViews.MemberRef;

/** Pénalités : consultation, encaissement (US-038), remise gracieuse (US-039), débiteurs (US-040). */
@Service
public class FineService {

    /** Tris de la liste des débiteurs (UX-penalites ECR-35). */
    public enum DebtorSort {
        TOTAL,
        COUNT,
        NAME
    }

    private final FineRepository fines;
    private final PaymentRepository payments;
    private final LoanRepository loans;
    private final CopyCirculationService copies;
    private final MemberLookup members;
    private final EligibilityService eligibility;
    private final FineCalculator calculator;
    private final CirculationAudit audit;
    private final BusinessTime time;

    public FineService(FineRepository fines, PaymentRepository payments, LoanRepository loans,
                       CopyCirculationService copies, MemberLookup members, EligibilityService eligibility,
                       FineCalculator calculator, CirculationAudit audit, BusinessTime time) {
        this.fines = fines;
        this.payments = payments;
        this.loans = loans;
        this.copies = copies;
        this.members = members;
        this.eligibility = eligibility;
        this.calculator = calculator;
        this.audit = audit;
        this.time = time;
    }

    /** Pénalités impayées d'un adhérent, avec le titre de l'emprunt d'origine, et leur total. */
    @Transactional(readOnly = true)
    public MemberFines unpaid(long memberId) {
        List<Fine> unpaid = fines.findByMemberIdAndStatusOrderByCreatedAtAscIdAsc(memberId, FineStatus.UNPAID);
        Map<Long, Long> copyByLoan = new HashMap<>();
        loans.findAllById(unpaid.stream().map(Fine::getLoanId).distinct().toList())
                .forEach(loan -> copyByLoan.put(loan.getId(), loan.getCopyId()));
        Map<Long, CopySnapshot> copiesById = copies.describe(copyByLoan.values().stream().distinct().toList());
        List<FineView> views = unpaid.stream().map(fine -> {
            CopySnapshot copy = copiesById.get(copyByLoan.get(fine.getLoanId()));
            return new FineView(fine.getId(), fine.getReason(), fine.getLateDays(), copy == null ? null : copy.title(),
                    copy == null ? null : copy.copyNumber(), LocalDate.ofInstant(fine.getCreatedAt(), time.zone()),
                    fine.getAmount(), fine.getVersion());
        }).toList();
        BigDecimal total = unpaid.stream().map(Fine::getAmount).reduce(FineCalculator.ZERO, BigDecimal::add);
        return new MemberFines(views, total);
    }

    /**
     * US-038, RG-38 : la sélection est réglée en une fois pour son total exact ; si une pénalité a été réglée
     * entre-temps, rien n'est encaissé (AC-038-3, AC-038-5).
     */
    @Transactional
    public PaymentResult collect(String memberNumber, List<Long> fineIds, PaymentMethod method, BigDecimal amount,
                                 long agentAccountId) {
        MemberRef member = members.require(memberNumber);
        Set<Long> requested = new HashSet<>(fineIds);
        List<Fine> selected = fines.findAllById(requested);
        if (selected.size() != requested.size()
                || selected.stream().anyMatch(fine -> !fine.getMemberId().equals(member.id()))) {
            throw BusinessException.of(ErrorCode.FINE_NOT_FOUND, "Pénalité introuvable.");
        }
        if (selected.stream().anyMatch(fine -> fine.getStatus() != FineStatus.UNPAID)) {
            throw alreadySettled();
        }
        BigDecimal total = selected.stream().map(Fine::getAmount).reduce(FineCalculator.ZERO, BigDecimal::add);
        int comparison = amount.compareTo(total);
        if (comparison < 0) {
            throw BusinessException.of(ErrorCode.PARTIAL_PAYMENT_NOT_ALLOWED,
                    "Le paiement partiel n'est pas autorisé.");
        }
        if (comparison > 0) {
            throw BusinessException.of(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                            "Le montant encaissé doit être égal au total sélectionné (" + euros(total) + ").")
                    .withProperty("expectedAmount", total);
        }
        Payment payment = payments.save(new Payment(member.id(), total, method, agentAccountId, time.now()));
        selected.forEach(fine -> fine.pay(payment.getId(), time.now(), agentAccountId));
        try {
            fines.flush();
        } catch (ObjectOptimisticLockingFailureException concurrent) {
            throw alreadySettled();
        }
        audit.log(agentAccountId, AuditAction.CREATE, AuditObjectType.PAYMENT, payment.getId(), Map.of(
                "memberNumber", member.memberNumber(), "amount", total, "method", method.name(),
                "fineIds", selected.stream().map(Fine::getId).sorted().toList()));
        selected.forEach(fine -> audit.log(agentAccountId, AuditAction.STATE_CHANGE, AuditObjectType.FINE, fine.getId(),
                Map.of("from", "UNPAID", "to", "PAID", "paymentId", payment.getId())));
        return new PaymentResult(payment.getId(), total, method, selected.stream().map(Fine::getId).sorted().toList(),
                fines.unpaidTotal(member.id()).setScale(2),
                eligibility.evaluate(member.id(), member.status(), true).eligible());
    }

    /** US-039, RG-39 : remise par un administrateur (contrôlé par la sécurité), motif obligatoire. */
    @Transactional
    public WaiverResult waive(long fineId, String reason, long adminAccountId) {
        Fine fine = fines.findById(fineId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.FINE_NOT_FOUND, "Pénalité introuvable."));
        fine.waive(reason.trim(), time.now(), adminAccountId);
        try {
            fines.flush();
        } catch (ObjectOptimisticLockingFailureException concurrent) {
            throw BusinessException.of(ErrorCode.FINE_ALREADY_SETTLED, "Cette pénalité a déjà été réglée ou remise.");
        }
        audit.log(adminAccountId, AuditAction.STATE_CHANGE, AuditObjectType.FINE, fineId,
                Map.of("from", "UNPAID", "to", "WAIVED", "reason", reason.trim(), "amount", fine.getAmount()));
        MemberRef member = members.require(fine.getMemberId());
        return new WaiverResult(fineId, fine.getStatus(), fines.unpaidTotal(member.id()).setScale(2),
                eligibility.evaluate(member.id(), member.status(), true).eligible());
    }

    /** US-040 : débiteurs, « bloqué » au-delà du seuil (RG-35). */
    @Transactional(readOnly = true)
    public DebtorList debtors(DebtorSort sort, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Debtor> rows = switch (sort) {
            case TOTAL -> fines.debtorsByTotal(pageable);
            case COUNT -> fines.debtorsByCount(pageable);
            case NAME -> fines.debtorsByName(pageable);
        };
        BigDecimal threshold = calculator.blockingThreshold();
        Map<Long, MemberRef> membersById = members.refs(rows.map(Debtor::getMemberId).getContent());
        Page<DebtorView> views = rows.map(row -> {
            MemberRef member = membersById.get(row.getMemberId());
            return new DebtorView(member.memberNumber(), member.lastName(), member.firstName(), member.phone(),
                    member.email(), row.getFineCount(), row.getTotal(), row.getTotal().compareTo(threshold) > 0);
        });
        return new DebtorList(fines.countDebtors(), fines.totalUnpaid().setScale(2), threshold,
                PageResponse.from(views, view -> view));
    }

    private static BusinessException alreadySettled() {
        return BusinessException.of(ErrorCode.FINE_ALREADY_SETTLED,
                "Une ou plusieurs pénalités ont déjà été réglées. La liste a été actualisée.");
    }

    private static String euros(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.FRANCE);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount) + " €";
    }
}
