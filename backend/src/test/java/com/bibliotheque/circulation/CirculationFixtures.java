package com.bibliotheque.circulation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.service.CopyCirculationService.CopySnapshot;
import com.bibliotheque.circulation.domain.Fine;
import com.bibliotheque.circulation.domain.Loan;
import com.bibliotheque.circulation.domain.Reservation;
import com.bibliotheque.support.TestFixtures;

/** Données de test de la circulation (identifiants posés par réflexion, comme après une sauvegarde). */
public final class CirculationFixtures {

    public static final long BOOK_ID = 7L;

    private CirculationFixtures() {
    }

    public static CopySnapshot copy(long id, CopyStatus status) {
        return new CopySnapshot(id, String.format("%08d", id), BOOK_ID, "L'Étranger", "Albert Camus", "R CAM", status);
    }

    public static Loan loan(long id, long copyId, long memberId, LocalDate loanDate, LocalDate dueDate) {
        Loan loan = new Loan(copyId, memberId, loanDate, dueDate, 2L);
        ReflectionTestUtils.setField(loan, "id", id);
        ReflectionTestUtils.setField(loan, "version", 0);
        return loan;
    }

    public static Reservation pending(long id, long memberId, Instant reservedAt) {
        Reservation reservation = new Reservation(BOOK_ID, memberId, reservedAt, 2L);
        ReflectionTestUtils.setField(reservation, "id", id);
        ReflectionTestUtils.setField(reservation, "version", 0);
        return reservation;
    }

    public static Reservation ready(long id, long memberId, long copyId, LocalDate deadline) {
        Reservation reservation = pending(id, memberId, TestFixtures.NOW.minusSeconds(86_400));
        reservation.makeReady(copyId, TestFixtures.NOW, deadline);
        return reservation;
    }

    public static Fine lateFine(long id, long memberId, long loanId, String amount) {
        Fine fine = Fine.lateReturn(memberId, loanId, 5, new BigDecimal(amount), TestFixtures.NOW);
        ReflectionTestUtils.setField(fine, "id", id);
        ReflectionTestUtils.setField(fine, "version", 0);
        return fine;
    }
}
