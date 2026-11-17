package com.careconnect.scheduling.repository;

import java.time.Instant;
import java.util.List;

import com.careconnect.scheduling.domain.ProviderAvailability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderAvailabilityRepository extends JpaRepository<ProviderAvailability, Long> {

    @Query("select count(a) > 0 from ProviderAvailability a where a.providerId = :providerId "
        + "and a.status = com.careconnect.scheduling.domain.AvailabilityStatus.ACTIVE "
        + "and a.startTime < :end and a.endTime > :start")
    boolean existsActiveOverlapping(@Param("providerId") Long providerId, @Param("start") Instant start,
                                    @Param("end") Instant end);

    @Query("select a from ProviderAvailability a where a.providerId = :providerId "
        + "and a.status = com.careconnect.scheduling.domain.AvailabilityStatus.ACTIVE "
        + "and a.startTime < :to and a.endTime > :from order by a.startTime")
    List<ProviderAvailability> findActiveInRange(@Param("providerId") Long providerId, @Param("from") Instant from,
                                                 @Param("to") Instant to);
}
