package com.bibliotheque.circulation.web;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.circulation.service.CirculationReadService;
import com.bibliotheque.circulation.service.CirculationViews.HistoryLoan;
import com.bibliotheque.circulation.service.CirculationViews.MemberFines;
import com.bibliotheque.circulation.service.CirculationViews.MemberLoan;
import com.bibliotheque.circulation.service.CirculationViews.MemberReservation;
import com.bibliotheque.circulation.service.FineService;
import com.bibliotheque.circulation.service.ReservationService;
import com.bibliotheque.common.web.PageRequests;
import com.bibliotheque.common.web.PageResponse;
import com.bibliotheque.member.service.MemberLookup;

/** Onglets de la fiche adhérent (ECR-18) : emprunts, réservations, pénalités et historique. */
@RestController
@RequestMapping("/api/v1/members/{memberNumber:A\\d{6}}")
public class MemberCirculationController {

    private final MemberLookup members;
    private final CirculationReadService reads;
    private final ReservationService reservations;
    private final FineService fines;

    public MemberCirculationController(MemberLookup members, CirculationReadService reads,
                                       ReservationService reservations, FineService fines) {
        this.members = members;
        this.reads = reads;
        this.reservations = reservations;
        this.fines = fines;
    }

    @GetMapping("/loans")
    public List<MemberLoan> loans(@PathVariable String memberNumber) {
        return reads.memberLoans(members.require(memberNumber).id());
    }

    @GetMapping("/reservations")
    public List<MemberReservation> reservations(@PathVariable String memberNumber) {
        return reservations.memberReservations(members.require(memberNumber).id());
    }

    @GetMapping("/fines")
    public MemberFines fines(@PathVariable String memberNumber) {
        return fines.unpaid(members.require(memberNumber).id());
    }

    @GetMapping("/history")
    public PageResponse<HistoryLoan> history(@PathVariable String memberNumber,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return PageResponse.from(reads.history(members.require(memberNumber).id(),
                PageRequests.of(page, size, null, Set.of(), Sort.unsorted())), item -> item);
    }
}
