package com.bibliotheque.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.AppProperties;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.support.TestFixtures;

class BootstrapAdminInitializerTest {

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final AuditService audit = mock(AuditService.class);

    @Test
    void neFaitRienSiUnAdministrateurExiste() {
        when(accounts.existsByRoleAndStatusNot(AccountRole.ADMIN, AccountStatus.DISABLED)).thenReturn(true);

        initializer("admin@exemple.fr", "Administrateur2026").run(null);

        verify(accounts, never()).save(any());
    }

    @Test
    void neFaitRienSansConfiguration() {
        initializer("", "").run(null);

        verify(accounts, never()).save(any());
    }

    @Test
    void neFaitRienSiLEmailEstDejaUtilise() {
        when(accounts.existsByEmail("admin@exemple.fr")).thenReturn(true);

        initializer("Admin@Exemple.fr", "Administrateur2026").run(null);

        verify(accounts, never()).save(any());
    }

    @Test
    void arreteLeDemarrageSiLeMotDePasseNEstPasConforme() {
        assertThatThrownBy(() -> initializer("admin@exemple.fr", "faible").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RG-01");
    }

    @Test
    void creeUnAdministrateurAvecMotDePasseTemporaire() {
        when(passwords.hashNewPassword("Administrateur2026", "password")).thenReturn("{bcrypt}hash");
        when(accounts.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        initializer("Admin@Exemple.fr", "Administrateur2026").run(null);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(AccountRole.ADMIN);
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@exemple.fr");
        assertThat(saved.getValue().isPasswordTemporary()).isTrue();
        verify(passwords).recordCurrentInHistory(saved.getValue());
    }

    private BootstrapAdminInitializer initializer(String email, String password) {
        AppProperties base = TestFixtures.properties();
        AppProperties properties = new AppProperties(base.timeZone(),
                new AppProperties.BootstrapAdmin(email, password), base.security(), base.audit(),
                base.privacy());
        return new BootstrapAdminInitializer(accounts, passwords, new PasswordPolicy(properties), audit,
                new BusinessTime(Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC), properties), properties);
    }
}
