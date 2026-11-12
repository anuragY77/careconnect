package com.careconnect.audit.domain;

/**
 * What happened. Stored by name; never rename a constant. New actions are added with the feature
 * that produces them.
 */
public enum AuditAction {
    LOGIN,
    LOGOUT,
    PASSWORD_CHANGED,
    STAFF_CREATED,
    STAFF_UPDATED,
    STAFF_DEACTIVATED,
    STAFF_ACTIVATED,
    STAFF_UNLOCKED,
    STAFF_PASSWORD_RESET,
    PATIENT_REGISTERED,
    PATIENT_UPDATED,
    ALLERGY_ADDED,
    ALLERGY_STATUS_CHANGED
}
