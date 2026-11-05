package com.careconnect.audit.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.careconnect.audit.domain.AuditAction;
import com.careconnect.audit.domain.AuditOutcome;

/**
 * What a caller wants to record. Time, client address and (unless {@link #by} is used) the
 * signed-in user are added by {@link AuditService} when the entry is written.
 */
public final class AuditEntry {

    final AuditAction action;
    final AuditOutcome outcome;
    boolean actorGiven;
    Long userId;
    String username;
    String role;
    Long patientId;
    String resourceType;
    String resourceId;
    final Map<String, String> details = new LinkedHashMap<>();

    private AuditEntry(AuditAction action, AuditOutcome outcome) {
        this.action = action;
        this.outcome = outcome;
    }

    public static AuditEntry of(AuditAction action, AuditOutcome outcome) {
        return new AuditEntry(action, outcome);
    }

    /** Names the actor explicitly (login, where nobody is signed in yet). Any value may be null. */
    public AuditEntry by(Long userId, String username, String role) {
        this.actorGiven = true;
        this.userId = userId;
        this.username = username;
        this.role = role;
        return this;
    }

    public AuditEntry on(String resourceType, Object resourceId) {
        this.resourceType = resourceType;
        this.resourceId = resourceId == null ? null : String.valueOf(resourceId);
        return this;
    }

    public AuditEntry patient(Long patientId) {
        this.patientId = patientId;
        return this;
    }

    /** Identifiers and before/after values only; never a password, token or free clinical text. */
    public AuditEntry detail(String key, Object value) {
        details.put(key, String.valueOf(value));
        return this;
    }
}
