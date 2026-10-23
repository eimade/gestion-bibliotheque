package com.bibliotheque.member.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.member.domain.Guardian;
import com.bibliotheque.member.domain.Member;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberCirculation.Eligibility;
import com.bibliotheque.member.service.MemberCirculation.Obligations;
import com.bibliotheque.member.service.MembershipPolicy.Renewal;

/**
 * Vues renvoyées par les services du module (records, jamais d'entité) ; leurs champs sont ceux des DTO
 * d'ARCH-adherents §3. Le statut présenté est le statut effectif à la date du jour (RG-16).
 */
public final class MemberViews {

    private MemberViews() {
    }

    public record GuardianView(String lastName, String firstName, String phone) {

        static GuardianView of(Guardian guardian) {
            return guardian == null ? null : new GuardianView(guardian.lastName(), guardian.firstName(), guardian.phone());
        }
    }

    public record MemberSummary(String memberNumber, String lastName, String firstName, LocalDate birthDate,
                                MemberStatus status, LocalDate expiryDate) {

        static MemberSummary of(Member member, LocalDate today) {
            return new MemberSummary(member.getMemberNumber(), member.getLastName(), member.getFirstName(),
                    member.getBirthDate(), member.effectiveStatus(today), member.getExpiryDate());
        }
    }

    public record RenewalView(boolean allowed, LocalDate newExpiryDate, LocalDate renewableFrom, String blockedBy) {

        static RenewalView of(Renewal renewal) {
            return new RenewalView(renewal.allowed(), renewal.newExpiryDate(), renewal.renewableFrom(),
                    renewal.blockedBy());
        }
    }

    public record MemberDetail(String memberNumber, String lastName, String firstName, LocalDate birthDate,
                               String email, String phone, String street, String postalCode, String city,
                               GuardianView guardian, LocalDate registrationDate, LocalDate expiryDate,
                               MemberStatus status, String suspensionReason, Long accountId,
                               AccountStatus accountStatus, Integer version, Eligibility eligibility,
                               RenewalView renewal, boolean expiresSoon, int activeReservations) {
    }

    public record ArchiveCheck(boolean archivable, List<String> reasons, int activeLoans, int activeReservations,
                               BigDecimal unpaidTotal) {

        static ArchiveCheck of(Obligations obligations) {
            List<String> reasons = obligations.blockingReasons();
            return new ArchiveCheck(reasons.isEmpty(), reasons, obligations.activeLoans(),
                    obligations.activeReservations(), obligations.unpaidTotal());
        }
    }

    public record MemberToArchive(String memberNumber, String lastName, String firstName, LocalDate expiryDate,
                                  boolean archivable, List<String> reasons, int activeLoans, int activeReservations,
                                  BigDecimal unpaidTotal) {

        static MemberToArchive of(Member member, Obligations obligations) {
            ArchiveCheck check = ArchiveCheck.of(obligations);
            return new MemberToArchive(member.getMemberNumber(), member.getLastName(), member.getFirstName(),
                    member.getExpiryDate(), check.archivable(), check.reasons(), check.activeLoans(),
                    check.activeReservations(), check.unpaidTotal());
        }
    }

    public record MyMember(String memberNumber, String lastName, String firstName, LocalDate birthDate, String email,
                           String phone, String street, String postalCode, String city, MemberStatus status,
                           LocalDate expiryDate, Integer version) {
    }

    /** Adhérent vu par le module circulation (identité, statut effectif, contacts). */
    public record MemberRef(long id, String memberNumber, String lastName, String firstName, MemberStatus status,
                            String phone, String email) {
    }
}
