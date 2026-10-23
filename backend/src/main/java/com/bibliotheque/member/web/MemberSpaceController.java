package com.bibliotheque.member.web;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.member.service.MemberSpaceService;
import com.bibliotheque.member.service.MemberViews.MyMember;
import com.bibliotheque.member.web.MemberRequests.ContactRequest;

/** Espace adhérent : identité et coordonnées de l'adhérent de la session (US-023, US-024). */
@RestController
@RequestMapping("/api/v1/me")
public class MemberSpaceController {

    private final MemberSpaceService space;

    public MemberSpaceController(MemberSpaceService space) {
        this.space = space;
    }

    @GetMapping("/member")
    public MyMember me(@AuthenticationPrincipal AccountPrincipal principal) {
        return space.me(principal.accountId());
    }

    @PutMapping("/contact")
    public MyMember updateContact(@Valid @RequestBody ContactRequest body,
                                  @AuthenticationPrincipal AccountPrincipal principal) {
        return space.updateContact(principal.accountId(), body.toContact(), body.version());
    }
}
