package com.bibliotheque.member.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.auth.service.MemberAccountService;
import com.bibliotheque.auth.service.MemberAccountService.MemberAccount;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.member.domain.Member;
import com.bibliotheque.member.repository.MemberRepository;
import com.bibliotheque.member.service.MemberViews.MemberRef;

/**
 * Adhérents vus par le module circulation (ARCH-adherents §1) : identité, statut effectif, contacts et
 * verrou de ligne qui sérialise les opérations d'un même adhérent (quotas RG-22, RG-30).
 */
@Service
public class MemberLookup {

    private final MemberRepository members;
    private final MemberAccountService accounts;
    private final BusinessTime time;

    public MemberLookup(MemberRepository members, MemberAccountService accounts, BusinessTime time) {
        this.members = members;
        this.accounts = accounts;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public MemberRef require(String memberNumber) {
        return ref(members.findByMemberNumber(memberNumber).orElseThrow(() -> MemberErrors.notFound(memberNumber)));
    }

    @Transactional(readOnly = true)
    public MemberRef require(long memberId) {
        return ref(members.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("Adhérent introuvable : " + memberId)));
    }

    /** Verrouille la ligne de l'adhérent jusqu'à la fin de la transaction appelante. */
    @Transactional
    public MemberRef lock(String memberNumber) {
        return ref(members.lockByMemberNumber(memberNumber).orElseThrow(() -> MemberErrors.notFound(memberNumber)));
    }

    @Transactional
    public MemberRef lock(long memberId) {
        return ref(members.lockById(memberId)
                .orElseThrow(() -> new IllegalStateException("Adhérent introuvable : " + memberId)));
    }

    /** Plusieurs adhérents avec leur e-mail, en deux lectures (listes paginées). */
    @Transactional(readOnly = true)
    public Map<Long, MemberRef> refs(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MemberAccount> accountsByMember = accounts.findByMembers(memberIds);
        LocalDate today = time.today();
        return members.findAllById(memberIds).stream()
                .map(member -> ref(member, accountsByMember.get(member.getId()), today))
                .collect(Collectors.toMap(MemberRef::id, Function.identity()));
    }

    /** NFR-06 : adhérent rattaché au compte de la session ; refus pour un compte sans adhérent. */
    @Transactional(readOnly = true)
    public long memberIdOfAccount(long accountId) {
        return accounts.memberIdOf(accountId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.ACCESS_DENIED,
                        "Vous n'avez pas les droits nécessaires pour effectuer cette action."));
    }

    private MemberRef ref(Member member) {
        return ref(member, accounts.findByMember(member.getId()).orElse(null), time.today());
    }

    private static MemberRef ref(Member member, MemberAccount account, LocalDate today) {
        return new MemberRef(member.getId(), member.getMemberNumber(), member.getLastName(), member.getFirstName(),
                member.effectiveStatus(today), member.getPhone(), account == null ? null : account.email());
    }
}
