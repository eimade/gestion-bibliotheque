package com.bibliotheque.member.repository;

import java.time.LocalDate;
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

import com.bibliotheque.member.domain.Member;
import com.bibliotheque.member.domain.MemberStatus;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByMemberNumber(String memberNumber);

    /** Verrou de ligne partagé par les opérations de circulation et l'archivage (ARCH-socle §5). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.memberNumber = :memberNumber")
    Optional<Member> lockByMemberNumber(@Param("memberNumber") String memberNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> lockById(@Param("id") Long id);

    /**
     * US-017 : nom, prénom ou e-mail du compte rattaché ({@code pattern} vient de {@code LikePatterns.contains}).
     * L'e-mail est lu sur l'entité du module auth (ARCH-adherents §2, Q-40).
     */
    @Query("""
            select m from Member m
            where m.status in :statuses
              and (:pattern is null
                   or m.lastName like :pattern escape '!'
                   or m.firstName like :pattern escape '!'
                   or m.id in (select a.memberId from UserAccount a where a.email like :pattern escape '!'))
            """)
    Page<Member> search(@Param("statuses") Collection<MemberStatus> statuses, @Param("pattern") String pattern,
                        Pageable pageable);

    /** AC-016-7 : homonyme non archivé (comparaison sous la collation, insensible à la casse et aux accents). */
    Optional<Member> findFirstByLastNameAndFirstNameAndBirthDateAndStatusNot(String lastName, String firstName,
                                                                            LocalDate birthDate,
                                                                            MemberStatus excluded);

    /** RG-19 (D1) : adhérents expirés avant {@code limit}, à proposer à l'archivage. */
    @Query("select m from Member m where m.status = :status and m.expiryDate < :limit")
    Page<Member> findByStatusAndExpiryDateBefore(@Param("status") MemberStatus status,
                                                 @Param("limit") LocalDate limit, Pageable pageable);

    /** RG-16 : prochain lot d'adhésions échues. */
    @Query("select m from Member m where m.status = :status and m.expiryDate < :today order by m.id")
    List<Member> findExpiring(@Param("status") MemberStatus status, @Param("today") LocalDate today,
                              Pageable pageable);
}
