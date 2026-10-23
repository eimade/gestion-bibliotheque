package com.bibliotheque.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bibliotheque.auth.domain.PasswordHistoryEntry;

public interface PasswordHistoryEntryRepository extends JpaRepository<PasswordHistoryEntry, Long> {

    /** Empreintes du compte, de la plus récente à la plus ancienne. */
    List<PasswordHistoryEntry> findByUserAccountIdOrderByCreatedAtDescIdDesc(Long userAccountId);
}
