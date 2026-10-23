package com.bibliotheque.common.audit;

/** Types d'action journalisés (valeurs de {@code ck_audit_entries_action}). */
public enum AuditAction {
    CREATE,
    UPDATE,
    STATE_CHANGE,
    DELETE,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT
}
