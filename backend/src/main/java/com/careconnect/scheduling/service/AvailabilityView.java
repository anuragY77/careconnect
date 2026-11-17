package com.careconnect.scheduling.service;

import java.time.Instant;

import com.careconnect.scheduling.domain.AvailabilityStatus;
import com.careconnect.scheduling.domain.ProviderAvailability;

public record AvailabilityView(long availabilityId, long providerId, Instant startTime, Instant endTime,
                               int slotMinutes, int slotCount, AvailabilityStatus status) {

    static AvailabilityView from(ProviderAvailability a) {
        return new AvailabilityView(a.getId(), a.getProviderId(), a.getStartTime(), a.getEndTime(), a.getSlotMinutes(),
            a.slotCount(), a.getStatus());
    }
}
