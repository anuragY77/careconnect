package com.careconnect.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.careconnect.TestcontainersConfiguration;
import com.careconnect.audit.domain.AuditAction;
import com.careconnect.audit.domain.AuditEvent;
import com.careconnect.audit.domain.AuditOutcome;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** The audit table against a real PostgreSQL: events are stored whole, and nothing can change them. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditStoreIntegrationTest {

    @Autowired
    private AuditStore store;

    @Autowired
    private JdbcTemplate jdbc;

    private long append(AuditEvent event) {
        store.append(event);
        return jdbc.queryForObject("select max(event_id) from audit_event", Long.class);
    }

    @Test
    void storesEveryFieldIncludingDetailsAsJson() {
        String marker = UUID.randomUUID().toString();
        Instant at = Instant.parse("2026-03-01T09:00:00Z");

        long id = append(new AuditEvent(at, 7L, "dr.rao", "DOCTOR", AuditAction.LOGIN, AuditOutcome.SUCCESS,
            42L, "PATIENT", marker, "10.0.0.5", Map.of("note", "quote \" and \\ and\nnewline", "k", "v")));

        Map<String, Object> row = jdbc.queryForMap("select * from audit_event where event_id = ?", id);
        assertThat(row).containsEntry("user_id", 7L).containsEntry("username", "dr.rao")
            .containsEntry("role", "DOCTOR").containsEntry("action", "LOGIN")
            .containsEntry("outcome", "SUCCESS").containsEntry("patient_id", 42L)
            .containsEntry("resource_type", "PATIENT").containsEntry("resource_id", marker)
            .containsEntry("client_ip", "10.0.0.5");
        assertThat(jdbc.queryForObject("select occurred_at = ?::timestamptz from audit_event where event_id = ?",
            Boolean.class, at.toString(), id)).isTrue();
        assertThat(jdbc.queryForObject("select details->>'note' from audit_event where event_id = ?",
            String.class, id)).isEqualTo("quote \" and \\ and\nnewline");
        assertThat(jdbc.queryForObject("select details->>'k' from audit_event where event_id = ?",
            String.class, id)).isEqualTo("v");
    }

    @Test
    void anUnknownUserAndMissingOptionalFieldsAreAllowed() {
        long id = append(new AuditEvent(Instant.now(), null, "nobody", null, AuditAction.LOGIN,
            AuditOutcome.FAILURE, null, null, null, null, null));

        assertThat(jdbc.queryForObject("select user_id is null and details is null from audit_event where event_id = ?",
            Boolean.class, id)).isTrue();
    }

    @Test
    void rowsCannotBeUpdatedDeletedOrTruncated() {
        long id = append(new AuditEvent(Instant.now(), 1L, "a", "ADMIN", AuditAction.LOGIN,
            AuditOutcome.SUCCESS, null, null, null, null, null));

        // Spring wraps the PostgreSQL error, so the trigger's message is on the root cause.
        assertThatThrownBy(() -> jdbc.update("update audit_event set username = 'x' where event_id = ?", id))
            .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_event where event_id = ?", id))
            .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate audit_event"))
            .rootCause().hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("select username from audit_event where event_id = ?", String.class, id))
            .isEqualTo("a");
    }

    @Test
    void theDatabaseRefusesAnUnknownOutcome() {
        assertThatThrownBy(() -> jdbc.update(
            "insert into audit_event (occurred_at, action, outcome) values (now(), 'LOGIN', 'MAYBE')"))
            .rootCause().hasMessageContaining("ck_audit_event_outcome");
    }
}
