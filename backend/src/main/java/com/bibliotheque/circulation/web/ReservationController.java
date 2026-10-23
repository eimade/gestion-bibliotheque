package com.bibliotheque.circulation.web;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

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

import com.bibliotheque.circulation.service.CirculationViews.QueueEntry;
import com.bibliotheque.circulation.service.CirculationViews.ReadyReservation;
import com.bibliotheque.circulation.service.CirculationViews.ReleasedCopy;
import com.bibliotheque.circulation.service.CirculationViews.ReservationCancelled;
import com.bibliotheque.circulation.service.CirculationViews.ReservationCreated;
import com.bibliotheque.circulation.service.CirculationViews.ReservationPreview;
import com.bibliotheque.circulation.service.ReservationService;
import com.bibliotheque.circulation.web.CirculationRequests.StaffReservationRequest;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.web.PageRequests;
import com.bibliotheque.common.web.PageResponse;
import com.bibliotheque.member.service.MemberLookup;

/** Réservations côté personnel (US-032 AC-032-8, US-033 → US-035). */
@RestController
@RequestMapping("/api/v1")
public class ReservationController {

    private final ReservationService reservations;
    private final MemberLookup members;

    public ReservationController(ReservationService reservations, MemberLookup members) {
        this.reservations = reservations;
        this.members = members;
    }

    @GetMapping("/reservations/preview")
    public ReservationPreview preview(@RequestParam long bookId,
                                      @RequestParam @Pattern(regexp = CirculationRequests.MEMBER_NUMBER,
                                              message = CirculationRequests.MEMBER_NUMBER_MESSAGE) String memberNumber) {
        return reservations.preview(bookId, members.require(memberNumber), true);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationCreated create(@Valid @RequestBody StaffReservationRequest body,
                                     @AuthenticationPrincipal AccountPrincipal principal) {
        return reservations.create(body.bookId(), body.memberNumber(), principal.accountId());
    }

    @PostMapping("/reservations/{id}/cancellation")
    public ReservationCancelled cancel(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal principal) {
        return reservations.cancel(id, null, principal.accountId());
    }

    @GetMapping("/books/{id}/reservations")
    public List<QueueEntry> queue(@PathVariable long id) {
        return reservations.queue(id);
    }

    @GetMapping("/reservations/ready")
    public PageResponse<ReadyReservation> ready(@RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return PageResponse.from(reservations.ready(PageRequests.of(page, size, null, Set.of(), Sort.unsorted())),
                item -> item);
    }

    @GetMapping("/reservations/released")
    public PageResponse<ReleasedCopy> released(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return PageResponse.from(reservations.released(PageRequests.of(page, size, null, Set.of(), Sort.unsorted())),
                item -> item);
    }
}
