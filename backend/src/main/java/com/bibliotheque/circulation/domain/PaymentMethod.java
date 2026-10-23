package com.bibliotheque.circulation.domain;

/** Mode de paiement d'un encaissement (RG-38, {@code ck_payments_method}). */
public enum PaymentMethod {
    CASH,
    CARD,
    CHECK
}
