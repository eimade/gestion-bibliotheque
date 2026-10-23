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

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.SessionRevoker;
import com.bibliotheque.support.TestFixtures;
import com.bibliotheque.support.TestSettings;

class MemberAccountServiceTest {

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final SessionRevoker revoker = mock(SessionRevoker.class);
    private final AuditService audit = mock(AuditService.class);
    private MemberAccountService service;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        service = new MemberAccountService(accounts, passwords, revoker, audit, TestSettings.time());
        account = TestFixtures.member(50L, 1L, "MotDePasse2026");
        when(accounts.findByMemberId(1L)).thenReturn(Optional.of(account));
        when(accounts.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 60L);
            return saved;
        });
        when(passwords.hashNewPassword("MotDePasse2026", "password")).thenReturn("{bcrypt}hash");
    }

    @Test
    void creeUnCompteAdherentTemporaireAvecEmailNormalise() {
        var created = service.create(1L, " Jean.Martin@Exemple.FR ", "MotDePasse2026", 2L);

        assertThat(created.email()).isEqualTo("jean.martin@exemple.fr");
        assertThat(created.accountId()).isEqualTo(60L);
        verify(accounts).save(org.mockito.ArgumentMatchers.argThat(saved -> saved.getRole() == AccountRole.MEMBER
                && saved.isPasswordTemporary() && saved.getMemberId() == 1L));
        verify(audit).recordAs(eq(2L), eq(AuditAction.CREATE), eq(AuditObjectType.ACCOUNT), eq("60"), anyMap());
    }

    @Test
    void refuseUnEmailDejaUtilise() {
        when(accounts.existsByEmail("pris@exemple.fr")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, "Pris@exemple.fr", "MotDePasse2026", 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_USED));
        assertThatThrownBy(() -> service.changeEmail(1L, "pris@exemple.fr", 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_USED));
    }

    @Test
    void changeLEmailDeConnexionSeulementSIlDiffere() {
        service.changeEmail(1L, account.getEmail().toUpperCase(), 2L);
        verify(audit, never()).recordAs(any(Long.class), any(), any(), any(), anyMap());

        service.changeEmail(1L, "nouveau@exemple.fr", 2L);
        assertThat(account.getEmail()).isEqualTo("nouveau@exemple.fr");
    }

    @Test
    void archiveEffaceLEmailDesactiveEtRevoqueLesSessions() {
        service.archive(1L);

        assertThat(account.getEmail()).isNull();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DISABLED);
        verify(revoker).revokeAll(50L);
    }

    @Test
    void lecturesDesComptesAdherents() {
        when(accounts.findByMemberIdIn(List.of(1L))).thenReturn(List.of(account));
        when(accounts.findById(50L)).thenReturn(Optional.of(account));
        when(accounts.findById(2L)).thenReturn(Optional.of(TestFixtures.staff(2L, AccountRole.LIBRARIAN, "x")));

        assertThat(service.findByMembers(List.of(1L))).containsKey(1L);
        assertThat(service.findByMembers(List.of())).isEmpty();
        assertThat(service.findByMember(1L)).isPresent();
        assertThat(service.memberIdOf(50L)).contains(1L);
        assertThat(service.memberIdOf(2L)).isEmpty();
        service.checkPassword("MotDePasse2026");
        verify(passwords, org.mockito.Mockito.atLeastOnce()).hashNewPassword("MotDePasse2026", "password");
    }

    @Test
    void unCompteDuPersonnelNePeutPasEtreAnonymise() {
        UserAccount staff = TestFixtures.staff(2L, AccountRole.LIBRARIAN, "x");

        assertThatThrownBy(staff::anonymizeForArchive).isInstanceOf(IllegalStateException.class);
    }
}
