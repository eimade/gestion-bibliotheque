package com.bibliotheque.circulation.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.LoanStatus;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    /** Emprunt en cours d'un exemplaire (au plus un : {@code uk_loans_active_copy}). */
    Optional<Loan> findFirstByCopyIdAndStatusIn(Long copyId, Collection<LoanStatus> statuses);

    long countByMemberIdAndStatusIn(Long memberId, Collection<LoanStatus> statuses);

    List<Loan> findByMemberIdAndStatusInOrderByDueDateAscIdAsc(Long memberId, Collection<LoanStatus> statuses);

    /** RG-23 : emprunt en retard, y compris avant le traitement de minuit (ADR-006 §3). */
    @Query("""
            select count(l) > 0 from Loan l
            where l.memberId = :memberId
              and (l.status = com.bibliotheque.circulation.domain.LoanStatus.OVERDUE
                   or (l.status = com.bibliotheque.circulation.domain.LoanStatus.ACTIVE and l.dueDate < :today))
            """)
    boolean hasOverdueLoan(@Param("memberId") Long memberId, @Param("today") LocalDate today);

    /** RG-31 : l'adhérent a un exemplaire de la notice en cours d'emprunt. */
    @Query("""
            select count(l) > 0 from Loan l, Copy c
            where c.id = l.copyId and c.book.id = :bookId and l.memberId = :memberId and l.status in :statuses
            """)
    boolean memberHasBookOnLoan(@Param("memberId") Long memberId, @Param("bookId") Long bookId,
                                @Param("statuses") Collection<LoanStatus> statuses);

    /** AC-023-2, ECR-18 : emprunts rendus depuis {@code since} (RG-19), du plus récent au plus ancien. */
    @Query("""
            select l from Loan l
            where l.memberId = :memberId and l.status = com.bibliotheque.circulation.domain.LoanStatus.RETURNED
              and l.returnDate >= :since
            order by l.returnDate desc, l.id desc
            """)
    Page<Loan> findHistory(@Param("memberId") Long memberId, @Param("since") LocalDate since, Pageable pageable);

    /** US-028 : emprunts en cours échus au plus tard le {@code dueOnOrBefore} ; le tri est fourni par l'appelant. */
    @Query("""
            select l from Loan l
            where l.status in :statuses and l.dueDate <= :dueOnOrBefore
            """)
    Page<Loan> findOverdue(@Param("statuses") Collection<LoanStatus> statuses,
                           @Param("dueOnOrBefore") LocalDate dueOnOrBefore, Pageable pageable);

    /** US-028 trié par nom d'adhérent (lecture de l'entité du module member, autorisée par ADR-001). */
    @Query(value = """
            select l from Loan l, Member m
            where m.id = l.memberId and l.status in :statuses and l.dueDate <= :dueOnOrBefore
            order by m.lastName asc, m.firstName asc, l.dueDate asc
            """, countQuery = """
            select count(l) from Loan l
            where l.status in :statuses and l.dueDate <= :dueOnOrBefore
            """)
    Page<Loan> findOverdueByMemberName(@Param("statuses") Collection<LoanStatus> statuses,
                                       @Param("dueOnOrBefore") LocalDate dueOnOrBefore, Pageable pageable);

    /** US-031 : historique d'un exemplaire, du plus récent au plus ancien. */
    Page<Loan> findByCopyIdOrderByLoanDateDescIdDesc(Long copyId, Pageable pageable);

    /** RG-27 : prochain lot d'emprunts actifs échus. */
    @Query("""
            select l from Loan l
            where l.status = com.bibliotheque.circulation.domain.LoanStatus.ACTIVE and l.dueDate < :today
            order by l.id
            """)
    List<Loan> findActiveDueBefore(@Param("today") LocalDate today, Pageable pageable);

    /** RG-19 : emprunts rendus avant {@code returnedBefore}, encore liés, sans pénalité impayée. */
    @Query("""
            select l from Loan l
            where l.status = com.bibliotheque.circulation.domain.LoanStatus.RETURNED
              and l.memberId is not null and l.returnDate < :returnedBefore
              and not exists (select f.id from Fine f
                              where f.loanId = l.id and f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID)
            order by l.id
            """)
    List<Loan> findToAnonymize(@Param("returnedBefore") LocalDate returnedBefore, Pageable pageable);

    /** Emprunts en cours par adhérent (obligations RG-18). */
    @Query("""
            select l.memberId as memberId, count(l) as total from Loan l
            where l.memberId in :memberIds and l.status in :statuses
            group by l.memberId
            """)
    List<MemberCount> countByMembers(@Param("memberIds") Collection<Long> memberIds,
                                     @Param("statuses") Collection<LoanStatus> statuses);
}
