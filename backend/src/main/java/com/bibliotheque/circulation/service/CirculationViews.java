package com.bibliotheque.circulation.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.bibliotheque.circulation.domain.FineReason;
import com.bibliotheque.circulation.domain.FineStatus;
import com.bibliotheque.circulation.domain.LoanStatus;
import com.bibliotheque.circulation.domain.PaymentMethod;
import com.bibliotheque.circulation.domain.ReservationStatus;
import com.bibliotheque.common.web.PageResponse;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberCirculation.Eligibility;
import com.bibliotheque.member.service.MemberViews.MemberRef;

/** Vues de la circulation (records, jamais d'entité) ; leurs champs sont ceux des DTO d'ARCH-circulation §3. */
public final class CirculationViews {

    private CirculationViews() {
    }

    /** Où va l'exemplaire libéré : en rayon ou mis de côté pour une réservation (RG-32). */
    public enum Destination {
        SHELF,
        HOLD
    }

    public record MemberBrief(String memberNumber, String lastName, String firstName) {

        static MemberBrief of(MemberRef member) {
            return member == null ? null : new MemberBrief(member.memberNumber(), member.lastName(), member.firstName());
        }
    }

    public record MemberContactBrief(String memberNumber, String lastName, String firstName, String phone,
                                     String email) {

        static MemberContactBrief of(MemberRef member) {
            return member == null ? null : new MemberContactBrief(member.memberNumber(), member.lastName(),
                    member.firstName(), member.phone(), member.email());
        }
    }

    public record HoldView(String memberNumber, String lastName, String firstName, LocalDate pickupDeadline) {
    }

    public record CopyRelease(String copyNumber, String shelfMark, Destination destination, HoldView hold) {
    }

    public record RenewalView(boolean allowed, String blockedBy, List<String> memberReasons, LocalDate newDueDate) {
    }

    public record MemberLoan(long id, String copyNumber, long bookId, String title, LocalDate loanDate,
                             LocalDate dueDate, LoanStatus status, int renewalCount, int overdueDays,
                             BigDecimal provisionalFine, RenewalView renewal) {
    }

    public record HistoryLoan(long id, String copyNumber, String title, LocalDate loanDate, LocalDate dueDate,
                              LocalDate returnDate) {
    }

    public record LoanResult(long loanId, String copyNumber, String title, LocalDate dueDate,
                             boolean reservationFulfilled, Eligibility eligibility) {
    }

    public record FineBrief(long id, BigDecimal amount) {
    }

    public record ReturnResult(String copyNumber, String title, String shelfMark, MemberBrief member, int lateDays,
                               FineBrief fine, Destination destination, HoldView hold) {
    }

    public record OverdueLoan(long loanId, String copyNumber, String title, MemberContactBrief member,
                              LocalDate dueDate, int overdueDays, BigDecimal provisionalFine) {
    }

    public record LossAmounts(int lateDays, BigDecimal lateFine, BigDecimal lostItemFine, BigDecimal total) {
    }

    public record CopyLoanHistoryItem(LocalDate loanDate, LocalDate dueDate, LocalDate returnDate, LoanStatus status,
                                      MemberBrief borrower) {
    }

    public record MemberReservation(long id, long bookId, String title, ReservationStatus status, Integer position,
                                    long queueLength, Instant reservedAt, LocalDate pickupDeadline,
                                    String copyNumber) {
    }

    public record PreviewMember(String memberNumber, String lastName, String firstName, MemberStatus status) {
    }

    public record ReservationPreview(boolean reservable, List<String> reasons, List<String> memberReasons,
                                     long position, long activeReservations, int limit, int pickupDays,
                                     PreviewMember member) {
    }

    public record CurrentReservation(long id, ReservationStatus status, Integer position, long queueLength,
                                     LocalDate pickupDeadline) {
    }

    public record MyBookReservation(CurrentReservation current, ReservationPreview preview) {
    }

    public record ReservationCreated(long id, ReservationStatus status, long position, long queueLength) {
    }

    public record ReservationCancelled(long id, ReservationStatus status, CopyRelease release) {
    }

    public record QueueEntry(long id, ReservationStatus status, Integer position, MemberBrief member,
                             Instant reservedAt, LocalDate pickupDeadline, String copyNumber) {
    }

    public record ReadyReservation(long id, String title, String copyNumber, String shelfMark,
                                   MemberContactBrief member, LocalDate pickupDeadline, boolean lastDay,
                                   boolean notificationFailed) {
    }

    public record ReleasedCopy(long reservationId, String title, String copyNumber, String shelfMark,
                               MemberBrief previousMember, LocalDate closedOn, ReservationStatus closedStatus,
                               Destination destination, HoldView hold) {
    }

    public record FineView(long id, FineReason reason, Integer lateDays, String title, String copyNumber,
                           LocalDate createdOn, BigDecimal amount, Integer version) {
    }

    public record MemberFines(List<FineView> fines, BigDecimal total) {
    }

    public record PaymentResult(long paymentId, BigDecimal amount, PaymentMethod method, List<Long> paidFineIds,
                                BigDecimal unpaidTotal, boolean eligible) {
    }

    public record WaiverResult(long fineId, FineStatus status, BigDecimal unpaidTotal, boolean eligible) {
    }

    public record DebtorView(String memberNumber, String lastName, String firstName, String phone, String email,
                             long fineCount, BigDecimal total, boolean blocked) {
    }

    public record DebtorList(long memberCount, BigDecimal totalAmount, BigDecimal threshold,
                             PageResponse<DebtorView> page) {
    }
}
