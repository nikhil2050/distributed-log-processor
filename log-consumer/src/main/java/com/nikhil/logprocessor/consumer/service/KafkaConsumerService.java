package com.nikhil.logprocessor.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikhil.logprocessor.consumer.model.LogEvent;
import com.nikhil.logprocessor.consumer.repository.LogEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class KafkaConsumerService {

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "log-events", groupId = "log-consumer-group")
    public void consume(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            Acknowledgment acknowledgment) {

        try {
            log.debug("Received message from topic: {}, partition: {}", topic, partition);

            LogEvent logEvent = objectMapper.readValue(message, LogEvent.class);
            processLogEvent(logEvent);

            acknowledgment.acknowledge();

            log.debug("Successfully processed log event: {}", logEvent.getId());

        } catch (Exception e) {
            log.error("Failed to process message: {}", message, e);
            throw new RuntimeException("Failed to process log event", e);
        }
    }

    public void processLogEvent(LogEvent logEvent) {
        try {
            // Set processing timestamp
            logEvent.setProcessedAt(LocalDateTime.now());

            // Save to database
            logEventRepository.save(logEvent);

            // Additional processing based on log level
            if ("ERROR".equals(logEvent.getLevel())) {
                handleErrorLog(logEvent);
            }

            log.info("Processed log event: {} for organization: {}",
                    logEvent.getId(), logEvent.getOrganizationId());

        } catch (DataAccessException e) {
            log.error("Database error processing log event: {}", logEvent.getId(), e);
            throw e;
        }
    }

    private void handleErrorLog(LogEvent logEvent) {
        // Additional error log processing (e.g., alerting, metrics)
        log.warn("Error log detected: {} from {}", logEvent.getMessage(), logEvent.getSource());

        // Could trigger alerts, update error counters, etc.
        // For now, just log the error
    }
}
