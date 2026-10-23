package com.bibliotheque.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.auth.service.MemberDirectory.MemberIdentity;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.support.TestFixtures;

class SessionUserServiceTest {

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final MemberDirectory memberDirectory = mock(MemberDirectory.class);
    private final SessionUserService service = new SessionUserService(accounts, memberDirectory);

    @Test
    void decritUnCompteDuPersonnelSansNumeroDAdherent() {
        SessionUser user = service.describe(TestFixtures.staff(1L, AccountRole.ADMIN, "Admin2026Secret"));

        assertThat(user.lastName()).isEqualTo("Dupont");
        assertThat(user.memberNumber()).isNull();
        assertThat(user.passwordTemporary()).isTrue();
    }

    @Test
    void decritUnAdherentAvecLIdentiteDeSaFiche() {
        when(memberDirectory.findIdentity(90L)).thenReturn(Optional.of(new MemberIdentity("A000123", "MARTIN", "Jean")));

        SessionUser user = service.describe(TestFixtures.member(9L, 90L, "Membre2026Secret"));

        assertThat(user.memberNumber()).isEqualTo("A000123");
        assertThat(user.lastName()).isEqualTo("MARTIN");
        assertThat(user.role()).isEqualTo(AccountRole.MEMBER);
    }

    @Test
    void echoueSiLAdherentRattacheEstIntrouvable() {
        assertThatThrownBy(() -> service.describe(TestFixtures.member(9L, 90L, "Membre2026Secret")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void neRenvoiePasLaSessionDUnCompteDesactive() {
        UserAccount disabled = TestFixtures.staff(4L, AccountRole.LIBRARIAN, "Agent2026Secret");
        disabled.disable();
        when(accounts.findById(4L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.current(4L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));
    }

    @Test
    void renvoieLaSessionDUnCompteActif() {
        when(accounts.findById(1L)).thenReturn(Optional.of(TestFixtures.staff(1L, AccountRole.ADMIN, "Admin2026Secret")));

        assertThat(service.current(1L).accountId()).isEqualTo(1L);
    }
}
