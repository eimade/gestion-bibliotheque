package com.bibliotheque.circulation.web;

import java.util.Locale;
import java.util.Set;

import jakarta.validation.Valid;

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

import com.bibliotheque.circulation.service.CirculationViews.DebtorList;
import com.bibliotheque.circulation.service.CirculationViews.PaymentResult;
import com.bibliotheque.circulation.service.CirculationViews.WaiverResult;
import com.bibliotheque.circulation.service.FineService;
import com.bibliotheque.circulation.service.FineService.DebtorSort;
import com.bibliotheque.circulation.web.CirculationRequests.PaymentRequest;
import com.bibliotheque.circulation.web.CirculationRequests.WaiverRequest;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.web.PageRequests;

/** Encaissements (US-038), remises (US-039, administrateur seul : SecurityConfig) et débiteurs (US-040). */
@RestController
@RequestMapping("/api/v1")
public class FineController {

    private final FineService fines;

    public FineController(FineService fines) {
        this.fines = fines;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResult collect(@Valid @RequestBody PaymentRequest body,
                                 @AuthenticationPrincipal AccountPrincipal principal) {
        return fines.collect(body.memberNumber(), body.fineIds(), body.method(), body.amount(), principal.accountId());
    }

    @PostMapping("/fines/{id}/waiver")
    public WaiverResult waive(@PathVariable long id, @Valid @RequestBody WaiverRequest body,
                              @AuthenticationPrincipal AccountPrincipal principal) {
        return fines.waive(id, body.reason(), principal.accountId());
    }

    /** {@code sort} : {@code total} (défaut, décroissant), {@code count} ou {@code name}. */
    @GetMapping("/fines/debtors")
    public DebtorList debtors(@RequestParam(required = false) String sort,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        Pageable pageable = PageRequests.of(page, size, null, Set.of(), Sort.unsorted());
        return fines.debtors(debtorSort(sort), pageable.getPageNumber(), pageable.getPageSize());
    }

    private static DebtorSort debtorSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DebtorSort.TOTAL;
        }
        return switch (sort.split(",")[0].toLowerCase(Locale.ROOT)) {
            case "total" -> DebtorSort.TOTAL;
            case "count" -> DebtorSort.COUNT;
            case "name" -> DebtorSort.NAME;
            default -> throw BusinessException.of(ErrorCode.VALIDATION_FAILED, "Tri non autorisé : total, count ou name.");
        };
    }
}
