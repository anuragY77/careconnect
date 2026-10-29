package com.careconnect.common.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs for the whole application. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
