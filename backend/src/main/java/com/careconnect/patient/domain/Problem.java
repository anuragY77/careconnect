package com.careconnect.patient.domain;

import java.time.Instant;
import java.time.LocalDate;

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

@Entity
@Table(name = "problem")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "problem_id")
    private Long id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private Long patientId;

    @Column(name = "icd10_code", nullable = false, updatable = false, length = 10)
    private String icd10Code;

    @Column(name = "description", nullable = false, updatable = false, length = 200)
    private String description;

    @Column(name = "onset_date", updatable = false)
    private LocalDate onsetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProblemStatus status;

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

    protected Problem() {
    }

    public static Problem record(long patientId, String icd10Code, String description, LocalDate onsetDate,
                                 long recordedBy) {
        if (icd10Code == null || icd10Code.isBlank()) {
            throw new IllegalArgumentException("icd10Code is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        Problem p = new Problem();
        p.patientId = patientId;
        p.icd10Code = icd10Code;
        p.description = description;
        p.onsetDate = onsetDate;
        p.status = ProblemStatus.ACTIVE;
        p.recordedBy = recordedBy;
        return p;
    }

    public void changeStatus(ProblemStatus target, String reason) {
        if (target == null || !status.canChangeTo(target)) {
            throw new IllegalStateException("A problem cannot change from " + status + " to " + target);
        }
        if (target == ProblemStatus.ENTERED_IN_ERROR && (reason == null || reason.isBlank())) {
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
    public String getIcd10Code() { return icd10Code; }
    public String getDescription() { return description; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public ProblemStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public Long getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
