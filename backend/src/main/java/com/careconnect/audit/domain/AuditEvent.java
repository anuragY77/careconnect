package com.careconnect.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One line of the audit trail. Plain data, so the audit module does not depend on any other
 * module. {@code details} holds identifiers and before/after values only, never free clinical
 * text, and never a password or token.
 */
public record AuditEvent(Instant occurredAt, Long userId, String username, String role, AuditAction action,
                         AuditOutcome outcome, Long patientId, String resourceType, String resourceId,
                         String clientIp, Map<String, String> details) {

    public AuditEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
