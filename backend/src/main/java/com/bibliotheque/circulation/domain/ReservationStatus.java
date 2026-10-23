package com.bibliotheque.circulation.domain;

/** Statut d'une réservation ({@code ck_reservations_status}) ; « active » = PENDING et READY. */
public enum ReservationStatus {
    PENDING,
    READY,
    FULFILLED,
    EXPIRED,
    CANCELLED
}
