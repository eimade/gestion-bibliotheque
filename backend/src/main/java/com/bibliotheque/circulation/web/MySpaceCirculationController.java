package com.bibliotheque.circulation.web;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;

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
import com.bibliotheque.circulation.service.CirculationViews.HistoryLoan;
import com.bibliotheque.circulation.service.CirculationViews.MemberFines;
import com.bibliotheque.circulation.service.CirculationViews.MemberLoan;
import com.bibliotheque.circulation.service.CirculationViews.MemberReservation;
import com.bibliotheque.circulation.service.CirculationViews.MyBookReservation;
import com.bibliotheque.circulation.service.CirculationViews.ReservationCancelled;
import com.bibliotheque.circulation.service.CirculationViews.ReservationCreated;
import com.bibliotheque.circulation.service.FineService;
import com.bibliotheque.circulation.service.LoanService;
import com.bibliotheque.circulation.service.ReservationService;
import com.bibliotheque.circulation.web.CirculationRequests.MyReservationRequest;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.web.PageRequests;
import com.bibliotheque.common.web.PageResponse;
import com.bibliotheque.member.service.MemberLookup;

/**
 * Espace adhérent (US-023, US-027, US-032, US-033) : tout est filtré sur l'adhérent de la session ; une ressource
 * d'un autre adhérent est introuvable (AC-023-4, AC-027-6, AC-033-4).
 */
@RestController
@RequestMapping("/api/v1/me")
public class MySpaceCirculationController {

    private final MemberLookup members;
    private final CirculationReadService reads;
    private final LoanService loans;
    private final ReservationService reservations;
    private final FineService fines;

    public MySpaceCirculationController(MemberLookup members, CirculationReadService reads, LoanService loans,
                                        ReservationService reservations, FineService fines) {
        this.members = members;
        this.reads = reads;
        this.loans = loans;
        this.reservations = reservations;
        this.fines = fines;
    }

    @GetMapping("/loans")
    public List<MemberLoan> loans(@AuthenticationPrincipal AccountPrincipal principal) {
        return reads.memberLoans(memberId(principal));
    }

    @GetMapping("/reservations")
    public List<MemberReservation> reservations(@AuthenticationPrincipal AccountPrincipal principal) {
        return reservations.memberReservations(memberId(principal));
    }

    @GetMapping("/fines")
    public MemberFines fines(@AuthenticationPrincipal AccountPrincipal principal) {
        return fines.unpaid(memberId(principal));
    }

    @GetMapping("/history")
    public PageResponse<HistoryLoan> history(@AuthenticationPrincipal AccountPrincipal principal,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return PageResponse.from(reads.history(memberId(principal),
                PageRequests.of(page, size, null, Set.of(), Sort.unsorted())), item -> item);
    }

    @PostMapping("/loans/{id}/renewal")
    public MemberLoan renew(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal principal) {
        return loans.renew(id, memberId(principal), principal.accountId());
    }

    @GetMapping("/books/{bookId}/reservation")
    public MyBookReservation bookReservation(@PathVariable long bookId,
                                             @AuthenticationPrincipal AccountPrincipal principal) {
        return reservations.myBookReservation(bookId, memberId(principal));
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationCreated reserve(@Valid @RequestBody MyReservationRequest body,
                                      @AuthenticationPrincipal AccountPrincipal principal) {
        return reservations.create(body.bookId(), memberId(principal), principal.accountId());
    }

    @PostMapping("/reservations/{id}/cancellation")
    public ReservationCancelled cancel(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal principal) {
        return reservations.cancel(id, memberId(principal), principal.accountId());
    }

    private long memberId(AccountPrincipal principal) {
        return members.memberIdOfAccount(principal.accountId());
    }
}
