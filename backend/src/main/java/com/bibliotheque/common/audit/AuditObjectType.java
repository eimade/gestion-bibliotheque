package com.bibliotheque.common.audit;

/** Types d'objet journalisés (valeurs de {@code ck_audit_entries_object_type}). */
public enum AuditObjectType {
    ACCOUNT,
    MEMBER,
    BOOK,
    AUTHOR,
    CATEGORY,
    COPY,
    LOAN,
    RESERVATION,
    FINE,
    PAYMENT,
    SETTING
}
