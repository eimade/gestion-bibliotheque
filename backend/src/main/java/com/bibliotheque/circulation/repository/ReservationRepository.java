package com.bibliotheque.circulation.repository;

import java.time.Instant;
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

import com.bibliotheque.circulation.domain.Reservation;
import com.bibliotheque.circulation.domain.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** RG-32 : file « premier arrivé, premier servi », tête de file verrouillée (ARCH-socle §5). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from Reservation r
            where r.bookId = :bookId and r.status = com.bibliotheque.circulation.domain.ReservationStatus.PENDING
            order by r.reservedAt asc, r.id asc
            """)
    List<Reservation> lockQueueHead(@Param("bookId") Long bookId, Pageable pageable);

    /** Réservation prête pour laquelle l'exemplaire est mis de côté (au plus une : {@code uk_reservations_ready_copy}). */
    Optional<Reservation> findFirstByCopyIdAndStatus(Long copyId, ReservationStatus status);

    long countByMemberIdAndStatusIn(Long memberId, Collection<ReservationStatus> statuses);

    boolean existsByMemberIdAndBookIdAndStatusIn(Long memberId, Long bookId, Collection<ReservationStatus> statuses);

    Optional<Reservation> findFirstByMemberIdAndBookIdAndStatusIn(Long memberId, Long bookId,
                                                                 Collection<ReservationStatus> statuses);

    /** RG-25 : une réservation en attente sur la notice bloque la prolongation. */
    boolean existsByBookIdAndStatus(Long bookId, ReservationStatus status);

    List<Reservation> findByMemberIdAndStatusInOrderByReservedAtAscIdAsc(Long memberId,
                                                                         Collection<ReservationStatus> statuses);

    long countByBookIdAndStatus(Long bookId, ReservationStatus status);

    /** Position dans la file : nombre de réservations en attente arrivées avant celle-ci, plus un. */
    @Query("""
            select count(r) + 1 from Reservation r
            where r.bookId = :bookId and r.status = com.bibliotheque.circulation.domain.ReservationStatus.PENDING
              and (r.reservedAt < :reservedAt or (r.reservedAt = :reservedAt and r.id < :id))
            """)
    long positionOf(@Param("bookId") Long bookId, @Param("reservedAt") Instant reservedAt, @Param("id") Long id);

    /** US-034 : réservations actives d'une notice, la prête d'abord puis la file. */
    @Query("""
            select r from Reservation r
            where r.bookId = :bookId and r.status in :statuses
            order by case when r.status = com.bibliotheque.circulation.domain.ReservationStatus.READY then 0 else 1 end,
                     r.reservedAt asc, r.id asc
            """)
    List<Reservation> findQueue(@Param("bookId") Long bookId, @Param("statuses") Collection<ReservationStatus> statuses);

    /** US-035 : réservations prêtes, triées par date limite croissante. */
    @Query("""
            select r from Reservation r where r.status = com.bibliotheque.circulation.domain.ReservationStatus.READY
            order by r.pickupDeadline asc, r.id asc
            """)
    Page<Reservation> findReady(Pageable pageable);

    /** US-035, Q-26 : réservations prêtes closes sans retrait (expirées ou annulées) depuis {@code since}. */
    @Query("""
            select r from Reservation r
            where r.status in :statuses and r.copyId is not null and r.closedAt >= :since
            order by r.closedAt desc, r.id desc
            """)
    Page<Reservation> findReleasedSince(@Param("statuses") Collection<ReservationStatus> statuses,
                                        @Param("since") Instant since, Pageable pageable);

    /** RG-33 : prochain lot de réservations prêtes dont la date limite est dépassée. */
    @Query("""
            select r from Reservation r
            where r.status = com.bibliotheque.circulation.domain.ReservationStatus.READY and r.pickupDeadline < :today
            order by r.id
            """)
    List<Reservation> findPickupOverdue(@Param("today") LocalDate today, Pageable pageable);

    @Query("""
            select r.memberId as memberId, count(r) as total from Reservation r
            where r.memberId in :memberIds and r.status in :statuses
            group by r.memberId
            """)
    List<MemberCount> countByMembers(@Param("memberIds") Collection<Long> memberIds,
                                     @Param("statuses") Collection<ReservationStatus> statuses);
}
