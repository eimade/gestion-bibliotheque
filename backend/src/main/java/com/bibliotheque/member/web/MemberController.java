package com.bibliotheque.member.web;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.web.PageRequests;
import com.bibliotheque.common.web.PageResponse;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberService;
import com.bibliotheque.member.service.MemberViews.ArchiveCheck;
import com.bibliotheque.member.service.MemberViews.MemberDetail;
import com.bibliotheque.member.service.MemberViews.MemberSummary;
import com.bibliotheque.member.service.MemberViews.MemberToArchive;
import com.bibliotheque.member.web.MemberRequests.MemberCreateRequest;
import com.bibliotheque.member.web.MemberRequests.MemberUpdateRequest;
import com.bibliotheque.member.web.MemberRequests.SuspendMemberRequest;

/** Gestion des adhérents par le personnel (US-016 → US-022), identifiés par leur numéro (RG-13). */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    /** Motif du numéro d'adhérent dans les chemins : « to-archive » ne s'y confond pas. */
    static final String NUMBER = "{memberNumber:A\\d{6}}";

    private final MemberService members;

    public MemberController(MemberService members) {
        this.members = members;
    }

    @GetMapping
    public PageResponse<MemberSummary> search(
            @RequestParam(required = false) @Size(min = 2, max = 100) String q,
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageRequests.of(page, size, sort, Set.of("lastName", "memberNumber", "expiryDate"),
                Sort.by("lastName", "firstName"));
        return PageResponse.from(members.search(q, status, pageable), item -> item);
    }

    @PostMapping
    public ResponseEntity<MemberDetail> register(@Valid @RequestBody MemberCreateRequest body,
                                                 @AuthenticationPrincipal AccountPrincipal principal) {
        MemberDetail created = members.register(body.toCommand(), body.password(), body.confirmDuplicate(),
                principal.accountId());
        return ResponseEntity.created(URI.create("/api/v1/members/" + created.memberNumber())).body(created);
    }

    @GetMapping("/to-archive")
    public PageResponse<MemberToArchive> toArchive(@RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by("expiryDate"));
        return PageResponse.from(members.toArchive(pageable), item -> item);
    }

    @GetMapping("/" + NUMBER)
    public MemberDetail get(@PathVariable String memberNumber) {
        return members.get(memberNumber);
    }

    @PutMapping("/" + NUMBER)
    public MemberDetail update(@PathVariable String memberNumber, @Valid @RequestBody MemberUpdateRequest body,
                               @AuthenticationPrincipal AccountPrincipal principal) {
        return members.update(memberNumber, body.toCommand(), body.version(), principal.accountId());
    }

    @PostMapping("/" + NUMBER + "/renewal")
    public MemberDetail renew(@PathVariable String memberNumber,
                              @AuthenticationPrincipal AccountPrincipal principal) {
        return members.renew(memberNumber, principal.accountId());
    }

    @PostMapping("/" + NUMBER + "/suspension")
    public MemberDetail suspend(@PathVariable String memberNumber, @Valid @RequestBody SuspendMemberRequest body,
                                @AuthenticationPrincipal AccountPrincipal principal) {
        return members.suspend(memberNumber, body.reason(), principal.accountId());
    }

    @PostMapping("/" + NUMBER + "/reactivation")
    public MemberDetail reactivate(@PathVariable String memberNumber,
                                   @AuthenticationPrincipal AccountPrincipal principal) {
        return members.reactivate(memberNumber, principal.accountId());
    }

    @GetMapping("/" + NUMBER + "/archive-check")
    public ArchiveCheck archiveCheck(@PathVariable String memberNumber) {
        return members.archiveCheck(memberNumber);
    }

    @PostMapping("/" + NUMBER + "/archive")
    public MemberDetail archive(@PathVariable String memberNumber,
                                @AuthenticationPrincipal AccountPrincipal principal) {
        return members.archive(memberNumber, principal.accountId());
    }
}
