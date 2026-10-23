package com.bibliotheque.circulation.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bibliotheque.circulation.domain.Fine;
import com.bibliotheque.circulation.domain.FineStatus;

public interface FineRepository extends JpaRepository<Fine, Long> {

    List<Fine> findByMemberIdAndStatusOrderByCreatedAtAscIdAsc(Long memberId, FineStatus status);

    /** RG-35 : total des pénalités impayées d'un adhérent (0,00 s'il n'en a pas). */
    @Query("""
            select coalesce(sum(f.amount), 0) from Fine f
            where f.memberId = :memberId and f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID
            """)
    BigDecimal unpaidTotal(@Param("memberId") Long memberId);

    @Query("""
            select f.memberId as memberId, sum(f.amount) as total from Fine f
            where f.memberId in :memberIds and f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID
            group by f.memberId
            """)
    List<MemberAmount> unpaidTotals(@Param("memberIds") Collection<Long> memberIds);

    /** US-040 : débiteurs par total décroissant (défaut, AC-040-1). */
    @Query(value = """
            select f.memberId as memberId, count(f) as fineCount, sum(f.amount) as total from Fine f
            where f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID
            group by f.memberId
            order by sum(f.amount) desc, f.memberId asc
            """, countQuery = """
            select count(distinct f.memberId) from Fine f
            where f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID
            """)
    Page<Debtor> debtorsByTotal(Pageable pageable);

    @Query(value = """
            select f.memberId as memberId, count(f) as fineCount, sum(f.amount) as total from Fine f
            where f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID
            group by f.memberId
            order by count(f) desc, sum(f.amount) desc, f.memberId asc
            """, countQuery = """
            select count(distinct f.memberId) from Fine f
            where f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID
            """)
    Page<Debtor> debtorsByCount(Pageable pageable);

    @Query(value = """
            select f.memberId as memberId, count(f) as fineCount, sum(f.amount) as total from Fine f, Member m
            where m.id = f.memberId and f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID
            group by f.memberId, m.lastName, m.firstName
            order by m.lastName asc, m.firstName asc, f.memberId asc
            """, countQuery = """
            select count(distinct f.memberId) from Fine f
            where f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID
            """)
    Page<Debtor> debtorsByName(Pageable pageable);

    @Query("""
            select coalesce(sum(f.amount), 0) from Fine f
            where f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID
            """)
    BigDecimal totalUnpaid();

    @Query("""
            select count(distinct f.memberId) from Fine f
            where f.status = com.bibliotheque.circulation.domain.FineStatus.UNPAID
            """)
    long countDebtors();

    interface Debtor {
        Long getMemberId();

        long getFineCount();

        BigDecimal getTotal();
    }
}
