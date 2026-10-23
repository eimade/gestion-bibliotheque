package com.bibliotheque.catalog.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bibliotheque.catalog.domain.Copy;
import com.bibliotheque.catalog.domain.CopyStatus;

public interface CopyRepository extends JpaRepository<Copy, Long> {

    Optional<Copy> findByCopyNumber(String copyNumber);

    /** Verrou de ligne pour les opérations de circulation (ARCH-circulation §5). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Copy c join fetch c.book where c.copyNumber = :copyNumber")
    Optional<Copy> lockByCopyNumber(@Param("copyNumber") String copyNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Copy c join fetch c.book where c.id = :id")
    Optional<Copy> lockById(@Param("id") Long id);

    @Query("select c from Copy c join fetch c.book where c.id in :ids")
    List<Copy> findWithBookByIdIn(@Param("ids") Collection<Long> ids);

    long countByBookIdAndStatus(Long bookId, CopyStatus status);

    long countByBookIdAndStatusIn(Long bookId, Collection<CopyStatus> statuses);

    List<Copy> findByBookIdOrderByCopyNumberAsc(Long bookId);

    long countByBookId(Long bookId);

    @Query("""
            select c.book.id as bookId,
                   sum(case when c.status = :available then 1 else 0 end) as available,
                   sum(case when c.status <> :withdrawn and c.status <> :lost then 1 else 0 end) as total,
                   count(c) as copies,
                   sum(case when c.status = :withdrawn then 1 else 0 end) as withdrawn
            from Copy c
            where c.book.id in :bookIds
            group by c.book.id
            """)
    List<CopyCounts> countsByBookIds(@Param("bookIds") Collection<Long> bookIds,
                                     @Param("available") CopyStatus available,
                                     @Param("withdrawn") CopyStatus withdrawn,
                                     @Param("lost") CopyStatus lost);

    default List<CopyCounts> countsByBookIds(Collection<Long> bookIds) {
        return countsByBookIds(bookIds, CopyStatus.AVAILABLE, CopyStatus.WITHDRAWN, CopyStatus.LOST);
    }
}
