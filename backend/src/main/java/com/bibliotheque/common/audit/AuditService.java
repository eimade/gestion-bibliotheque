package com.bibliotheque.common.audit;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.common.config.BusinessTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Écriture du journal d'audit (NFR-08). L'entrée rejoint la transaction de l'opération journalisée :
 * elle n'existe que si l'opération est validée.
 */
@Service
public class AuditService {

    public static final String SYSTEM_LABEL = "Système";

    private final AuditEntryRepository repository;
    private final ObjectMapper objectMapper;
    private final BusinessTime time;
    private final ActorLabelResolver actorLabels;

    public AuditService(AuditEntryRepository repository, ObjectMapper objectMapper, BusinessTime time,
                        ActorLabelResolver actorLabels) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.time = time;
        this.actorLabels = actorLabels;
    }

    @Transactional
    public void record(Long actorAccountId, String actorLabel, AuditAction action, AuditObjectType objectType,
                       String objectRef) {
        record(actorAccountId, actorLabel, action, objectType, objectRef, Map.of());
    }

    @Transactional
    public void record(Long actorAccountId, String actorLabel, AuditAction action, AuditObjectType objectType,
                       String objectRef, Map<String, ?> details) {
        repository.save(new AuditEntry(time.now(), actorAccountId, actorLabel, action, objectType, objectRef,
                details.isEmpty() ? null : toJson(details)));
    }

    /** Journalise une action d'un utilisateur connecté ; son libellé est résolu à partir de son compte. */
    @Transactional
    public void recordAs(long actorAccountId, AuditAction action, AuditObjectType objectType, String objectRef,
                         Map<String, ?> details) {
        record(actorAccountId, actorLabels.labelOf(actorAccountId), action, objectType, objectRef, details);
    }

    private String toJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Détail d'audit non sérialisable", e);
        }
    }
}
