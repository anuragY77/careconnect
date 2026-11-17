package com.careconnect.scheduling.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "provider_availability")
public class ProviderAvailability {

    public static final int MIN_SLOT_MINUTES = 5;
    public static final int MAX_SLOT_MINUTES = 240;
    public static final Duration MAX_WINDOW = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "availability_id")
    private Long id;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(name = "start_time", nullable = false, updatable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false, updatable = false)
    private Instant endTime;

    @Column(name = "slot_minutes", nullable = false, updatable = false)
    private int slotMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AvailabilityStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProviderAvailability() {
    }

    public static ProviderAvailability create(long providerId, Instant startTime, Instant endTime, int slotMinutes,
                                              long createdBy) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("start and end are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("end must be after start");
        }
        if (slotMinutes < MIN_SLOT_MINUTES || slotMinutes > MAX_SLOT_MINUTES) {
            throw new IllegalArgumentException("slotMinutes out of range");
        }
        Duration length = Duration.between(startTime, endTime);
        if (length.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("window too long");
        }
        if (length.toSeconds() % (slotMinutes * 60L) != 0) {
            throw new IllegalArgumentException("window must hold a whole number of slots");
        }
        ProviderAvailability a = new ProviderAvailability();
        a.providerId = providerId;
        a.startTime = startTime;
        a.endTime = endTime;
        a.slotMinutes = slotMinutes;
        a.status = AvailabilityStatus.ACTIVE;
        a.createdBy = createdBy;
        return a;
    }

    public int slotCount() {
        return (int) (Duration.between(startTime, endTime).toMinutes() / slotMinutes);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProviderId() { return providerId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public int getSlotMinutes() { return slotMinutes; }
    public AvailabilityStatus getStatus() { return status; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
