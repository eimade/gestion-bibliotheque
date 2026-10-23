package com.bibliotheque.circulation.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;

/** Journal d'audit de la circulation (NFR-08) : l'auteur est un compte, ou « Système » pour les traitements. */
@Component
class CirculationAudit {

    private final AuditService audit;

    CirculationAudit(AuditService audit) {
        this.audit = audit;
    }

    /** @param actorAccountId compte auteur, ou {@code null} pour un traitement planifié */
    void log(Long actorAccountId, AuditAction action, AuditObjectType type, Object ref, Map<String, ?> details) {
        if (actorAccountId == null) {
            audit.record(null, AuditService.SYSTEM_LABEL, action, type, String.valueOf(ref), details);
        } else {
            audit.recordAs(actorAccountId, action, type, String.valueOf(ref), details);
        }
    }
}
