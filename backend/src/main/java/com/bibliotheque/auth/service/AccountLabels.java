package com.bibliotheque.auth.service;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.UserAccount;

/**
 * Libellé d'auteur pour le journal d'audit (NFR-08) : « NOM Prénom » pour le personnel,
 * « Adhérent A000123 » pour un adhérent (pas de nom d'adhérent dans le journal).
 */
@Component
public class AccountLabels {

    private final MemberDirectory memberDirectory;

    public AccountLabels(MemberDirectory memberDirectory) {
        this.memberDirectory = memberDirectory;
    }

    public String of(UserAccount account) {
        if (account.getRole() == AccountRole.MEMBER) {
            return memberDirectory.findIdentity(account.getMemberId())
                    .map(identity -> "Adhérent " + identity.memberNumber())
                    .orElse("Adhérent");
        }
        return account.getLastName().toUpperCase(Locale.FRENCH) + " " + account.getFirstName();
    }
}
