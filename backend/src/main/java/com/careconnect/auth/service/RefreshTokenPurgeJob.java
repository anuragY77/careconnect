package com.careconnect.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes expired refresh tokens once a day, so the table does not grow without limit. */
@Component
public class RefreshTokenPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenPurgeJob.class);

    private final TokenService tokenService;

    public RefreshTokenPurgeJob(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "UTC")
    public void purge() {
        try {
            int deleted = tokenService.purgeExpired();
            log.info("Expired refresh tokens deleted: count={}", deleted);
        } catch (RuntimeException e) {
            log.error("Deleting expired refresh tokens failed", e);
        }
    }
}
