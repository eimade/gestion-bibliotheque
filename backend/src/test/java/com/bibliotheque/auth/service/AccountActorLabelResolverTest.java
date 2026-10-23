package com.bibliotheque.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.support.TestFixtures;

class AccountActorLabelResolverTest {

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final AccountLabels labels = mock(AccountLabels.class);
    private final AccountActorLabelResolver resolver = new AccountActorLabelResolver(accounts, labels);

    @Test
    void libelleDuCompte() {
        UserAccount account = TestFixtures.staff(2L, AccountRole.LIBRARIAN, "Agent2026Secret");
        when(accounts.findById(2L)).thenReturn(Optional.of(account));
        when(labels.of(account)).thenReturn("DUPONT Marie");

        assertThat(resolver.labelOf(2L)).isEqualTo("DUPONT Marie");
    }

    @Test
    void compteInconnu() {
        assertThat(resolver.labelOf(99L)).isEqualTo(AccountActorLabelResolver.UNKNOWN_ACCOUNT);
    }
}
