package com.careconnect.common.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one source of "now" for business rules (lockouts, expiry). Tests replace it
 * to move time forward instead of waiting. The clock is UTC; display zones are a UI matter (ADR-015).
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
