package com.bibliotheque.circulation.domain;

/** Statut d'un emprunt ({@code ck_loans_status}) ; « en cours » = ACTIVE et OVERDUE. */
public enum LoanStatus {
    ACTIVE,
    OVERDUE,
    RETURNED,
    LOST
}
