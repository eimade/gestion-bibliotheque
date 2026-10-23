package com.bibliotheque.member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.member.domain.Guardian;
import com.bibliotheque.member.domain.Member;
import com.bibliotheque.member.domain.MemberContact;
import com.bibliotheque.member.domain.MemberIdentity;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberCirculation.Eligibility;
import com.bibliotheque.member.service.MemberCommand;
import com.bibliotheque.member.service.MemberViews.MemberRef;

/** Adhérents de test (identifiants posés par réflexion, comme après une sauvegarde). */
public final class MemberFixtures {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private MemberFixtures() {
    }

    public static MemberIdentity adult() {
        return new MemberIdentity("Martin", "Jean", LocalDate.of(1990, 3, 12), null);
    }

    public static MemberContact contact() {
        return new MemberContact("0612345678", "12 rue des Lilas", "75011", "Paris");
    }

    public static MemberCommand command(MemberIdentity identity) {
        return new MemberCommand(identity, contact(), "Jean.Martin@exemple.fr");
    }

    public static Guardian guardian() {
        return new Guardian("Martin", "Paul", "0611111111");
    }

    public static Member member(long id, String number, LocalDate expiry, MemberStatus status) {
        Member member = new Member(number, adult(), contact(), LocalDate.of(2025, 1, 2), expiry);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "version", 0);
        ReflectionTestUtils.setField(member, "status", status);
        if (status == MemberStatus.SUSPENDED) {
            ReflectionTestUtils.setField(member, "suspensionReason", "Litige");
        }
        return member;
    }

    public static MemberRef ref(long id, MemberStatus status) {
        return new MemberRef(id, String.format("A%06d", id), "Martin", "Jean", status, "0612345678",
                "jean.martin@exemple.fr");
    }

    public static Eligibility eligible() {
        return new Eligibility(true, List.of(), 0, 5, 5, new BigDecimal("0.00"), new BigDecimal("5.00"));
    }
}
