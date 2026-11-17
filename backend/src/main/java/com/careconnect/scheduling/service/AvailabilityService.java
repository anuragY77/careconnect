package com.careconnect.scheduling.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.careconnect.audit.domain.AuditAction;
import com.careconnect.audit.domain.AuditOutcome;
import com.careconnect.audit.service.AuditEntry;
import com.careconnect.audit.service.AuditService;
import com.careconnect.auth.service.ProviderDirectory;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.scheduling.domain.ProviderAvailability;
import com.careconnect.scheduling.repository.ProviderAvailabilityRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

    static final Duration MAX_LIST_RANGE = Duration.ofDays(62);

    private final ProviderAvailabilityRepository availability;
    private final ProviderDirectory providers;
    private final AuditService audit;
    private final Clock clock;

    public AvailabilityService(ProviderAvailabilityRepository availability, ProviderDirectory providers,
                               AuditService audit, Clock clock) {
        this.availability = availability;
        this.providers = providers;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AvailabilityView create(long actingUserId, long providerId, Instant startTime, Instant endTime,
                                   Integer slotMinutes) {
        requireProvider(providerId);
        if (startTime == null || endTime == null || slotMinutes == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Start, end and slot length are required.");
        }
        if (!startTime.equals(startTime.truncatedTo(ChronoUnit.MINUTES))
            || !endTime.equals(endTime.truncatedTo(ChronoUnit.MINUTES))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Start and end must be on a whole minute.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The end must be after the start.");
        }
        if (slotMinutes < ProviderAvailability.MIN_SLOT_MINUTES || slotMinutes > ProviderAvailability.MAX_SLOT_MINUTES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The slot length must be between "
                + ProviderAvailability.MIN_SLOT_MINUTES + " and " + ProviderAvailability.MAX_SLOT_MINUTES + " minutes.");
        }
        Duration length = Duration.between(startTime, endTime);
        if (length.compareTo(ProviderAvailability.MAX_WINDOW) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A window can be at most 24 hours long.");
        }
        if (length.toMinutes() % slotMinutes != 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The window must hold a whole number of slots.");
        }
        if (startTime.isBefore(clock.instant())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The window cannot start in the past.");
        }
        if (availability.existsActiveOverlapping(providerId, startTime, endTime)) {
            throw new ApiException(ErrorCode.CONFLICT, "The provider already has availability that overlaps this window.");
        }
        ProviderAvailability saved = availability.save(
            ProviderAvailability.create(providerId, startTime, endTime, slotMinutes, actingUserId));
        audit.record(AuditEntry.of(AuditAction.AVAILABILITY_CREATED, AuditOutcome.SUCCESS)
            .on("AVAILABILITY", saved.getId())
            .detail("providerId", providerId)
            .detail("startTime", startTime.toString())
            .detail("endTime", endTime.toString())
            .detail("slotMinutes", slotMinutes));
        return AvailabilityView.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityView> list(long providerId, Instant from, Instant to) {
        requireProvider(providerId);
        if (from == null || to == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The from and to times are required.");
        }
        if (!to.isAfter(from)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The to time must be after the from time.");
        }
        if (Duration.between(from, to).compareTo(MAX_LIST_RANGE) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The range can be at most 62 days.");
        }
        return availability.findActiveInRange(providerId, from, to).stream().map(AvailabilityView::from).toList();
    }

    private void requireProvider(long providerId) {
        if (providers.find(providerId).isEmpty()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Provider not found.");
        }
    }
}
