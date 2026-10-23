package com.bibliotheque.common.audit;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /** Purge par lots de 500 (ADR-006 §2) ; renvoie le nombre d'entrées supprimées. */
    @Modifying
    @Query(value = "DELETE FROM audit_entries WHERE occurred_at < :limit LIMIT 500", nativeQuery = true)
    int deleteBatchOlderThan(@Param("limit") Instant limit);
}
