package com.bibliotheque.auth.domain;

/** Rôles applicatifs (valeurs de {@code ck_user_accounts_role}). */
public enum AccountRole {
    MEMBER,
    LIBRARIAN,
    ADMIN;

    public boolean isStaff() {
        return this != MEMBER;
    }
}
