package com.careconnect.audit.service;

import java.time.Clock;

import com.careconnect.audit.domain.AuditEvent;
import com.careconnect.audit.repository.AuditStore;
import com.careconnect.common.security.SecurityClaims;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit events in the transaction of the caller. The event and the change it describes
 * commit together or roll back together, and a failed audit write fails the request
 * (fail-closed, ARCHITECTURE 3.6). Calling it without a transaction is a programming error and
 * throws, so an event can never be written on its own by accident.
 *
 * <p>Failures that end in an exception (and so roll back) are not recorded here. A login
 * failure is recorded because {@code LoginService} returns a result instead of throwing.
 */
@Service
public class AuditService {

    private final AuditStore store;
    private final Clock clock;

    public AuditService(AuditStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        Long userId = entry.userId;
        String username = entry.username;
        String role = entry.role;
        if (!entry.actorGiven) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                userId = parseId(jwt.getSubject());
                role = jwt.getClaimAsString(SecurityClaims.ROLE);
            }
        }
        store.append(new AuditEvent(clock.instant(), userId, cut(username, 50), cut(role, 30), entry.action,
            entry.outcome, entry.patientId, cut(entry.resourceType, 40), cut(entry.resourceId, 64),
            cut(clientIp(), 45), entry.details));
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
            return request.getRequest().getRemoteAddr();
        }
        return null;
    }

    private static Long parseId(String subject) {
        try {
            return subject == null ? null : Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Fits a value into its column; a longer attacker-supplied username must not break the write. */
    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
