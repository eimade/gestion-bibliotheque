package com.bibliotheque.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserAccount> findByMemberId(Long memberId);

    List<UserAccount> findByMemberIdIn(Collection<Long> memberIds);

    boolean existsByRoleAndStatusNot(AccountRole role, AccountStatus status);

    /** Comptes d'un rôle hors statut donné, verrouillés en écriture (RG-06 : dernier administrateur). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccount a where a.role = :role and a.status <> :status")
    List<UserAccount> lockByRoleAndStatusNot(@Param("role") AccountRole role, @Param("status") AccountStatus status);

    /** Recherche des comptes du personnel ; {@code pattern} vient de {@code LikePatterns.contains}. */
    @Query("""
            select a from UserAccount a
            where a.role in :roles
              and (:role is null or a.role = :role)
              and (:status is null or a.status = :status)
              and (:pattern is null
                   or a.lastName like :pattern escape '!'
                   or a.firstName like :pattern escape '!'
                   or a.email like :pattern escape '!')
            """)
    Page<UserAccount> searchStaff(@Param("roles") Collection<AccountRole> roles,
                                  @Param("role") AccountRole role,
                                  @Param("status") AccountStatus status,
                                  @Param("pattern") String pattern,
                                  Pageable pageable);
}
