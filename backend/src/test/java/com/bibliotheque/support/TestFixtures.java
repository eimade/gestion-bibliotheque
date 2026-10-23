package com.bibliotheque.support;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.common.config.AppProperties;

/** Données de test partagées. */
public final class TestFixtures {

    public static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");
    /** BCrypt de coût minimal : mêmes règles qu'en production, tests rapides. */
    public static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private TestFixtures() {
    }

    public static AppProperties properties() {
        return new AppProperties(ZoneId.of("Europe/Paris"),
                new AppProperties.BootstrapAdmin("admin@exemple.fr", "Administrateur2026"),
                new AppProperties.Security(new AppProperties.Security.Login(5, Duration.ofMinutes(15)),
                        new AppProperties.Security.Password(12, 3)),
                new AppProperties.Audit(Period.ofMonths(12)),
                new AppProperties.Privacy(Period.ofMonths(4), Period.ofYears(3)));
    }

    public static UserAccount staff(long id, AccountRole role, String rawPassword) {
        UserAccount account = UserAccount.staff("agent" + id + "@exemple.fr", "Dupont", "Marie", role,
                ENCODER.encode(rawPassword), NOW);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "version", 0);
        return account;
    }

    /** Compte adhérent : créé par réflexion, la fabrique relève de la feature adherents. */
    public static UserAccount member(long id, long memberId, String rawPassword) {
        UserAccount account = staff(id, AccountRole.LIBRARIAN, rawPassword);
        ReflectionTestUtils.setField(account, "role", AccountRole.MEMBER);
        ReflectionTestUtils.setField(account, "memberId", memberId);
        ReflectionTestUtils.setField(account, "lastName", null);
        ReflectionTestUtils.setField(account, "firstName", null);
        return account;
    }
}
