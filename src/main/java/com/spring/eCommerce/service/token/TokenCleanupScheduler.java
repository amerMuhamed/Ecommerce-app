package com.spring.eCommerce.service.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled task to clean up expired tokens from the database.
 * Runs every 1 hour to prevent database from accumulating expired tokens.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class TokenCleanupScheduler {

    private final TokenInfoService tokenInfoService;

    /**
     * Runs every 1 hour (3600000 milliseconds) at fixed rate
     * Initial delay of 5 minutes to allow app startup
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 300000)
    @Transactional
    public void cleanupExpiredTokens() {
        log.debug("Starting scheduled token cleanup task");
        try {
            tokenInfoService.deleteExpiredTokens();
            log.info("Scheduled token cleanup completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled token cleanup", e);
        }
    }

    /**
     * Alternative: Runs at specific time (2 AM every day)
     * Uncomment to use instead of fixedRate version
     */
    // @Scheduled(cron = "0 0 2 * * *")
    // @Transactional
    // public void cleanupExpiredTokensDaily() {
    //     log.info("Starting daily token cleanup task");
    //     try {
    //         tokenInfoService.deleteExpiredTokens();
    //         log.info("Daily token cleanup completed successfully");
    //     } catch (Exception e) {
    //         log.error("Error during daily token cleanup", e);
    //     }
    // }
}

