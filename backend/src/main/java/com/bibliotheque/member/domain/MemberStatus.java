package com.bibliotheque.member.domain;

/** Cycle de vie d'un adhérent (SPEC-adherents §4, {@code ck_members_status}). */
public enum MemberStatus {
    ACTIVE,
    EXPIRED,
    SUSPENDED,
    ARCHIVED
}
