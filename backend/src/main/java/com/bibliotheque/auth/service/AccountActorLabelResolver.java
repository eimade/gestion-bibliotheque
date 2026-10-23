package com.bibliotheque.auth.service;

import org.springframework.stereotype.Component;

import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.audit.ActorLabelResolver;

/** Implémente le port d'audit : « NOM Prénom » pour le personnel, « Adhérent A000123 » pour un adhérent. */
@Component
public class AccountActorLabelResolver implements ActorLabelResolver {

    static final String UNKNOWN_ACCOUNT = "Compte inconnu";

    private final UserAccountRepository accounts;
    private final AccountLabels labels;

    public AccountActorLabelResolver(UserAccountRepository accounts, AccountLabels labels) {
        this.accounts = accounts;
        this.labels = labels;
    }

    @Override
    public String labelOf(long accountId) {
        return accounts.findById(accountId).map(labels::of).orElse(UNKNOWN_ACCOUNT);
    }
}
