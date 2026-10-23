package com.bibliotheque.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.service.MemberDirectory.MemberIdentity;
import com.bibliotheque.support.TestFixtures;

class AccountLabelsTest {

    private final MemberDirectory memberDirectory = mock(MemberDirectory.class);
    private final AccountLabels labels = new AccountLabels(memberDirectory);

    @Test
    void libelleDuPersonnelEnNomPrenom() {
        assertThat(labels.of(TestFixtures.staff(1L, AccountRole.ADMIN, "Admin2026Secret"))).isEqualTo("DUPONT Marie");
    }

    @Test
    void libelleDUnAdherentParSonNumeroSansSonNom() {
        when(memberDirectory.findIdentity(90L)).thenReturn(Optional.of(new MemberIdentity("A000123", "MARTIN", "Jean")));

        assertThat(labels.of(TestFixtures.member(9L, 90L, "Membre2026Secret"))).isEqualTo("Adhérent A000123");
    }

    @Test
    void libelleGeneriqueSiLAdherentEstIntrouvable() {
        assertThat(labels.of(TestFixtures.member(9L, 90L, "Membre2026Secret"))).isEqualTo("Adhérent");
    }
}
