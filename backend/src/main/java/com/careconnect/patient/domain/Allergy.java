package com.careconnect.patient.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One allergy of one patient. The substance, the patient and who recorded it never change; the status
 * changes only through {@link #changeStatus}, with a reason when the allergy stops being active.
 */
@Entity
@Table(name = "allergy")
public class Allergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allergy_id")
    private Long id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private Long patientId;

    @Column(name = "substance", nullable = false, updatable = false, length = 120)
    private String substance;

    @Column(name = "reaction", length = 200)
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private AllergySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AllergyStatus status;

    @Column(name = "status_reason", length = 200)
    private String statusReason;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Required by JPA. */
    protected Allergy() {
    }

    /** Creates an active allergy. Text values are expected to be trimmed already by the caller. */
    public static Allergy record(long patientId, String substance, String reaction, AllergySeverity severity,
                                 long recordedBy) {
        if (substance == null || substance.isBlank()) {
            throw new IllegalArgumentException("substance is required");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity is required");
        }
        Allergy a = new Allergy();
        a.patientId = patientId;
        a.substance = substance;
        a.reaction = reaction;
        a.severity = severity;
        a.status = AllergyStatus.ACTIVE;
        a.recordedBy = recordedBy;
        return a;
    }

    /** Moves to another status. Leaving ACTIVE needs a written reason; ENTERED_IN_ERROR is final. */
    public void changeStatus(AllergyStatus target, String reason) {
        if (target == null || !status.canChangeTo(target)) {
            throw new IllegalStateException("An allergy cannot change from " + status + " to " + target);
        }
        if (target != AllergyStatus.ACTIVE && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A reason is required");
        }
        this.status = target;
        this.statusReason = reason;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.recordedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPatientId() { return patientId; }
    public String getSubstance() { return substance; }
    public String getReaction() { return reaction; }
    public AllergySeverity getSeverity() { return severity; }
    public AllergyStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public Long getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
