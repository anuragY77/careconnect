package com.careconnect.audit.repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.careconnect.audit.domain.AuditEvent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Stores audit events in the {@code audit_event} table with plain SQL (no JPA entity, so nothing
 * can update a row by accident). It joins the transaction of the caller, so a business change and
 * its audit event commit or roll back together.
 */
@Repository
public class PostgresAuditStore implements AuditStore {

    private static final String INSERT = """
        insert into audit_event (occurred_at, user_id, username, role, action, outcome, patient_id,
                                 resource_type, resource_id, client_ip, details)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
        """;

    private final JdbcTemplate jdbc;

    public PostgresAuditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(AuditEvent e) {
        jdbc.update(INSERT, ps -> {
            ps.setTimestamp(1, Timestamp.from(e.occurredAt()));
            setLong(ps, 2, e.userId());
            ps.setString(3, e.username());
            ps.setString(4, e.role());
            ps.setString(5, e.action().name());
            ps.setString(6, e.outcome().name());
            setLong(ps, 7, e.patientId());
            ps.setString(8, e.resourceType());
            ps.setString(9, e.resourceId());
            ps.setString(10, e.clientIp());
            ps.setString(11, e.details().isEmpty() ? null : toJson(e.details()));
        });
    }

    private static void setLong(java.sql.PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    /** A flat JSON object of strings, written by hand so no JSON library is needed. */
    static String toJson(java.util.Map<String, String> details) {
        List<String> parts = new ArrayList<>();
        new java.util.TreeMap<>(details).forEach((k, v) -> parts.add(quote(k) + ":" + (v == null ? "null" : quote(v))));
        return "{" + String.join(",", parts) + "}";
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
