package com.bibliotheque.auth.web;

import java.net.URI;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.service.StaffAccountService;
import com.bibliotheque.auth.service.StaffAccountView;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.web.PageRequests;
import com.bibliotheque.common.web.PageResponse;

/** Comptes du personnel, réservés à l'ADMIN par la chaîne de sécurité (US-004, US-005). */
@RestController
@RequestMapping("/api/v1/staff-accounts")
public class StaffAccountController {

    private static final Set<String> SORT_FIELDS = Set.of("lastName", "role", "status");
    private static final Sort DEFAULT_SORT = Sort.by("lastName").and(Sort.by("firstName"));

    private final StaffAccountService staffAccounts;

    public StaffAccountController(StaffAccountService staffAccounts) {
        this.staffAccounts = staffAccounts;
    }

    @GetMapping
    public PageResponse<StaffAccountResponse> list(
            @RequestParam(required = false) @Size(min = 2, max = 100) String q,
            @RequestParam(required = false) AccountRole role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AccountPrincipal principal) {
        var pageable = PageRequests.of(page, size, sort, SORT_FIELDS, DEFAULT_SORT);
        return PageResponse.from(staffAccounts.search(q, role, status, pageable, principal.accountId()),
                StaffAccountResponse::from);
    }

    @GetMapping("/{id}")
    public StaffAccountResponse get(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal principal) {
        return StaffAccountResponse.from(staffAccounts.get(id, principal.accountId()));
    }

    @PostMapping
    public ResponseEntity<StaffAccountResponse> create(@Valid @RequestBody CreateStaffAccountRequest body,
                                                       @AuthenticationPrincipal AccountPrincipal principal) {
        StaffAccountView created = staffAccounts.create(body.toCommand(), principal.accountId());
        return ResponseEntity.created(URI.create("/api/v1/staff-accounts/" + created.id()))
                .body(StaffAccountResponse.from(created));
    }

    @PostMapping("/{id}/deactivation")
    public StaffAccountResponse deactivate(@PathVariable long id,
                                           @AuthenticationPrincipal AccountPrincipal principal) {
        return StaffAccountResponse.from(staffAccounts.deactivate(id, principal.accountId()));
    }

    @PostMapping("/{id}/reactivation")
    public StaffAccountResponse reactivate(@PathVariable long id,
                                           @AuthenticationPrincipal AccountPrincipal principal) {
        return StaffAccountResponse.from(staffAccounts.reactivate(id, principal.accountId()));
    }
}
