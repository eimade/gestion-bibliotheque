package com.bibliotheque.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.PasswordHistoryEntry;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.PasswordHistoryEntryRepository;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.support.TestFixtures;

class PasswordServiceTest {

    private static final String CURRENT = "Actuel2026Secret";

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PasswordHistoryEntryRepository history = mock(PasswordHistoryEntryRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AccountLabels labels = mock(AccountLabels.class);
    private PasswordService service;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        var properties = TestFixtures.properties();
        service = new PasswordService(accounts, history, TestFixtures.ENCODER, new PasswordPolicy(properties),
                new BusinessTime(Clock.fixed(TestFixtures.NOW, Clock.systemUTC().getZone()), properties),
                audit, labels, properties);
        account = TestFixtures.staff(7L, AccountRole.LIBRARIAN, CURRENT);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(labels.of(account)).thenReturn("DUPONT Marie");
    }

    @Test
    void refuseSiLeMotDePasseActuelEstIncorrect() {
        assertThatThrownBy(() -> service.changeOwnPassword(7L, "Faux2026Secret!", "Nouveau2026Secret"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CURRENT_PASSWORD_INCORRECT));
    }

    @Test
    void refuseUnNouveauMotDePasseNonConforme() {
        assertThatThrownBy(() -> service.changeOwnPassword(7L, CURRENT, "court"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);
                    assertThat(ex.properties()).containsEntry("field", "newPassword");
                });
    }

    @Test
    void refuseLaReutilisationDuMotDePasseActuel() {
        assertThatThrownBy(() -> service.changeOwnPassword(7L, CURRENT, CURRENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED));
    }

    @Test
    void refuseUnMotDePassePresentDansLesTroisDerniers() {
        String ancien = "Ancien2025Secret";
        when(history.findByUserAccountIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(
                entry(account.getPasswordHash()), entry(TestFixtures.ENCODER.encode(ancien))));

        assertThatThrownBy(() -> service.changeOwnPassword(7L, CURRENT, ancien))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED));
    }

    @Test
    void accepteUnMotDePassePlusAncienQueLesTroisDerniers() {
        String tresAncien = "TresAncien2020x";
        when(history.findByUserAccountIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(
                entry("{x}1"), entry("{x}2"), entry("{x}3"), entry(TestFixtures.ENCODER.encode(tresAncien))));

        service.changeOwnPassword(7L, CURRENT, tresAncien);

        assertThat(TestFixtures.ENCODER.matches(tresAncien, account.getPasswordHash())).isTrue();
    }

    @Test
    void changeLeMotDePasseQuiNEstPlusTemporaireEtJournalise() {
        service.changeOwnPassword(7L, CURRENT, "Nouveau2026Secret");

        assertThat(TestFixtures.ENCODER.matches("Nouveau2026Secret", account.getPasswordHash())).isTrue();
        assertThat(account.isPasswordTemporary()).isFalse();
        verify(history).save(any(PasswordHistoryEntry.class));
        verify(audit).record(eq(7L), eq("DUPONT Marie"), eq(AuditAction.UPDATE), eq(AuditObjectType.ACCOUNT),
                eq("7"), anyMap());
    }

    @Test
    void neConserveQueLesTroisDernieresEmpreintes() {
        List<PasswordHistoryEntry> entries = new ArrayList<>(List.of(entry("a"), entry("b"), entry("c"), entry("d")));
        when(history.findByUserAccountIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(entries);

        service.recordCurrentInHistory(account);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PasswordHistoryEntry>> deleted = ArgumentCaptor.forClass(List.class);
        verify(history).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(entries.get(3));
    }

    @Test
    void attribueUnMotDePasseTemporaire() {
        service.assignTemporaryPassword(account, "Temporaire2026x", "newPassword");

        assertThat(account.isPasswordTemporary()).isTrue();
        assertThat(TestFixtures.ENCODER.matches("Temporaire2026x", account.getPasswordHash())).isTrue();
        verify(audit, never()).record(any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void hashNewPasswordVerifieLaPolitique() {
        assertThatThrownBy(() -> service.hashNewPassword("court", "initialPassword"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.properties()).containsEntry("field", "initialPassword"));
        assertThat(TestFixtures.ENCODER.matches("Initial2026Secret",
                service.hashNewPassword("Initial2026Secret", "initialPassword"))).isTrue();
    }

    @Test
    void refuseUnCompteInexistant() {
        assertThatThrownBy(() -> service.changeOwnPassword(99L, CURRENT, "Nouveau2026Secret"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private static PasswordHistoryEntry entry(String hash) {
        return new PasswordHistoryEntry(7L, hash, TestFixtures.NOW);
    }
}
