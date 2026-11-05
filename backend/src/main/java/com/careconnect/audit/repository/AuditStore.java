package com.careconnect.audit.repository;

import com.careconnect.audit.domain.AuditEvent;

/**
 * Where audit events are kept. Callers only append; the trail is never changed or deleted. The
 * interface lets the storage move to another database later without touching the callers
 * (ARCHITECTURE 5.2). An implementation must throw if the event was not stored, so a failed audit
 * write fails the request (fail-closed).
 */
public interface AuditStore {

    void append(AuditEvent event);
}
