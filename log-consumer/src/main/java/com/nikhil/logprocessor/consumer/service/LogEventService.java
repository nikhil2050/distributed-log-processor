package com.nikhil.logprocessor.consumer.service;

import com.nikhil.logprocessor.consumer.model.LogEvent;
import com.nikhil.logprocessor.consumer.repository.LogEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.LocalDateTime;

/**
 * Service for processing log events with retry capability.
 * 
 * This service is injected as a Spring bean, so @Retryable annotations will work
 * because method calls go through the Spring proxy.
 */
@Service
@Slf4j
public class LogEventService {

    @Autowired
    private LogEventRepository logEventRepository;

    /**
     * Process a log event with automatic retry on database failures.
     * 
     * Retry Logic:
     * - Max attempts: 3
     * - Initial delay: 1 second
     * - Backoff multiplier: 2x (1s, 2s, 4s)
     * - Retryable exceptions: DataAccessException, CannotCreateTransactionException
     * 
     * @param logEvent the log event to process
     * @throws RuntimeException if all retry attempts fail
     */
    @Retryable(value = {DataAccessException.class, CannotCreateTransactionException.class},
                maxAttempts = 3,
                backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void processLogEvent(LogEvent logEvent) {
        try {
            log.debug("Processing log event: {}", logEvent.getId());

            // Set processing timestamp
            logEvent.setProcessedAt(LocalDateTime.now());

            // Save to database
            logEventRepository.save(logEvent);

            // Additional processing based on log level
            if ("ERROR".equals(logEvent.getLevel())) {
                handleErrorLog(logEvent);
            }

            log.info("Successfully processed log event: {} for organization: {}",
                    logEvent.getId(), logEvent.getOrganizationId());

        } catch (DataAccessException | CannotCreateTransactionException e) {
            log.warn("Database error processing log event: {} - will retry if attempts remaining", 
                    logEvent.getId());
            throw e;
        }
    }

    private void handleErrorLog(LogEvent logEvent) {
        // Additional error log processing (e.g., alerting, metrics)
        log.warn("Error log detected: {} from {}", logEvent.getMessage(), logEvent.getSource());
    }
}


