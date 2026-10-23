package com.bibliotheque.member.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.member.domain.Member;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.repository.MemberRepository;

/** RG-16 : traitement idempotent par lots de 500, journalisé avec l'auteur « Système » (ADR-006 §2). */
@Service
public class MembershipExpiryService {

    static final int BATCH_SIZE = 500;

    private final MemberRepository members;
    private final AuditService audit;
    private final BusinessTime time;

    public MembershipExpiryService(MemberRepository members, AuditService audit, BusinessTime time) {
        this.members = members;
        this.audit = audit;
        this.time = time;
    }

    /** @return nombre d'adhésions passées à l'état expiré dans ce lot */
    @Transactional
    public int expireNextBatch() {
        LocalDate today = time.today();
        List<Member> batch = members.findExpiring(MemberStatus.ACTIVE, today, PageRequest.of(0, BATCH_SIZE));
        for (Member member : batch) {
            member.expire();
            audit.record(null, AuditService.SYSTEM_LABEL, AuditAction.STATE_CHANGE, AuditObjectType.MEMBER,
                    member.getMemberNumber(), Map.of("from", MemberStatus.ACTIVE.name(),
                            "to", MemberStatus.EXPIRED.name()));
        }
        members.flush();
        return batch.size();
    }
}
