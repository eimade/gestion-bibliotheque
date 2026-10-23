package com.bibliotheque.circulation.web;

import java.util.Locale;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.circulation.service.CirculationReadService;
import com.bibliotheque.circulation.service.CirculationReadService.OverdueSort;
import com.bibliotheque.circulation.service.CirculationViews.CopyLoanHistoryItem;
import com.bibliotheque.circulation.service.CirculationViews.CopyRelease;
import com.bibliotheque.circulation.service.CirculationViews.LoanResult;
import com.bibliotheque.circulation.service.CirculationViews.LossAmounts;
import com.bibliotheque.circulation.service.CirculationViews.MemberLoan;
import com.bibliotheque.circulation.service.CirculationViews.OverdueLoan;
import com.bibliotheque.circulation.service.CirculationViews.ReturnResult;
import com.bibliotheque.circulation.service.LoanService;
import com.bibliotheque.circulation.web.CirculationRequests.LoanRequest;
import com.bibliotheque.circulation.web.CirculationRequests.ReturnRequest;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.web.PageRequests;
import com.bibliotheque.common.web.PageResponse;

/** Guichet et suivi des emprunts (US-025 → US-031), réservés au personnel. */
@RestController
@RequestMapping("/api/v1")
public class LoanController {

    private static final String COPY_NUMBER = "{copyNumber:\\d{8}}";

    private final LoanService loans;
    private final CirculationReadService reads;

    public LoanController(LoanService loans, CirculationReadService reads) {
        this.loans = loans;
        this.reads = reads;
    }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResult borrow(@Valid @RequestBody LoanRequest body,
                             @AuthenticationPrincipal AccountPrincipal principal) {
        return loans.borrow(body.memberNumber(), body.copyNumber(), principal.accountId());
    }

    @PostMapping("/returns")
    public ReturnResult giveBack(@Valid @RequestBody ReturnRequest body,
                                 @AuthenticationPrincipal AccountPrincipal principal) {
        return loans.giveBack(body.copyNumber(), principal.accountId());
    }

    @PostMapping("/loans/{id}/renewal")
    public MemberLoan renew(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal principal) {
        return loans.renew(id, null, principal.accountId());
    }

    /** US-028 ; {@code sort} : {@code overdueDays} (défaut), {@code dueDate} ou {@code memberName}. */
    @GetMapping("/loans/overdue")
    public PageResponse<OverdueLoan> overdue(@RequestParam(defaultValue = "1") @Min(1) int minDays,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        Pageable pageable = PageRequests.of(page, size, null, Set.of(), Sort.unsorted());
        return PageResponse.from(reads.overdue(minDays, overdueSort(sort), pageable.getPageNumber(),
                pageable.getPageSize()), item -> item);
    }

    @GetMapping("/loans/{id}/loss-preview")
    public LossAmounts lossPreview(@PathVariable long id) {
        return loans.lossPreview(id);
    }

    @PostMapping("/loans/{id}/loss")
    public LossAmounts declareLost(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal principal) {
        return loans.declareLost(id, principal.accountId());
    }

    @PostMapping("/copies/" + COPY_NUMBER + "/found")
    public CopyRelease markFound(@PathVariable String copyNumber,
                                 @AuthenticationPrincipal AccountPrincipal principal) {
        return loans.markFound(copyNumber, principal.accountId());
    }

    @GetMapping("/copies/" + COPY_NUMBER + "/loans")
    public PageResponse<CopyLoanHistoryItem> copyHistory(@PathVariable String copyNumber,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        Pageable pageable = PageRequests.of(page, size, null, Set.of(), Sort.unsorted());
        return PageResponse.from(reads.copyHistory(copyNumber, pageable), item -> item);
    }

    private static OverdueSort overdueSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return OverdueSort.OVERDUE_DAYS;
        }
        return switch (sort.split(",")[0].toLowerCase(Locale.ROOT)) {
            case "overduedays" -> OverdueSort.OVERDUE_DAYS;
            case "duedate" -> OverdueSort.DUE_DATE;
            case "membername" -> OverdueSort.MEMBER_NAME;
            default -> throw BusinessException.of(ErrorCode.VALIDATION_FAILED,
                    "Tri non autorisé : overdueDays, dueDate ou memberName.");
        };
    }
}
