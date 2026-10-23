package com.bibliotheque.member.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.auth.service.MemberAccountService;
import com.bibliotheque.auth.service.MemberAccountService.MemberAccount;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.member.domain.Member;
import com.bibliotheque.member.domain.MemberContact;
import com.bibliotheque.member.repository.MemberRepository;
import com.bibliotheque.member.service.MemberViews.MyMember;

/** Espace de l'adhérent connecté (US-023, US-024) : toujours l'adhérent de la session (NFR-06). */
@Service
public class MemberSpaceService {

    private final MemberRepository members;
    private final MemberLookup lookup;
    private final MemberAccountService accounts;
    private final AuditService audit;
    private final BusinessTime time;

    public MemberSpaceService(MemberRepository members, MemberLookup lookup, MemberAccountService accounts,
                              AuditService audit, BusinessTime time) {
        this.members = members;
        this.lookup = lookup;
        this.accounts = accounts;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public MyMember me(long accountId) {
        return view(load(accountId));
    }

    /** AC-024-1 : téléphone et adresse ; les autres champs se modifient au guichet (AC-024-2). */
    @Transactional
    public MyMember updateContact(long accountId, MemberContact contact, Integer version) {
        Member member = load(accountId);
        MemberErrors.requireVersion(version, member.getVersion());
        Map<String, Object> changes = new LinkedHashMap<>();
        compare(changes, "phone", member.getPhone(), contact.phone());
        compare(changes, "street", member.getStreet(), contact.street());
        compare(changes, "postalCode", member.getPostalCode(), contact.postalCode());
        compare(changes, "city", member.getCity(), contact.city());
        member.updateContact(contact);
        members.flush();
        if (!changes.isEmpty()) {
            audit.recordAs(accountId, AuditAction.UPDATE, AuditObjectType.MEMBER, member.getMemberNumber(), changes);
        }
        return view(member);
    }

    private Member load(long accountId) {
        long memberId = lookup.memberIdOfAccount(accountId);
        return members.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("Adhérent introuvable : " + memberId));
    }

    private MyMember view(Member member) {
        String email = accounts.findByMember(member.getId()).map(MemberAccount::email).orElse(null);
        return new MyMember(member.getMemberNumber(), member.getLastName(), member.getFirstName(),
                member.getBirthDate(), email, member.getPhone(), member.getStreet(), member.getPostalCode(),
                member.getCity(), member.effectiveStatus(time.today()), member.getExpiryDate(), member.getVersion());
    }

    private static void compare(Map<String, Object> changes, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("before", before);
            change.put("after", after);
            changes.put(field, change);
        }
    }
}
