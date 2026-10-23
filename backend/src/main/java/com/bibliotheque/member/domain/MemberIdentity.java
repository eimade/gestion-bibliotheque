package com.bibliotheque.member.domain;

import java.time.LocalDate;

/** Identité d'un adhérent ; {@code guardian} est {@code null} sans représentant légal. */
public record MemberIdentity(String lastName, String firstName, LocalDate birthDate, Guardian guardian) {
}
