package com.nikhil.logprocessor.producer.controller;

import com.nikhil.logprocessor.producer.model.LogEvent;
import com.nikhil.logprocessor.producer.service.KafkaProducerService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@RestController
@RequestMapping("/logproducer/api/logs")
@Slf4j
public class LogEventController {

    @Autowired
    private KafkaProducerService kafkaProducerService;

    private final Counter logCounter;

    public LogEventController(MeterRegistry meterRegistry) {
        this.logCounter = Counter.builder("log_events_received_total")  // Metric Update
                .description("Total number of log events received")
                .register(meterRegistry);
    }

    @PostMapping
    @Timed(value = "log_event_processing_time", description = "Time taken to process log event")
    public ResponseEntity<Map<String, String>> createLogEvent(@RequestBody LogEvent logEvent) {
        try {
            // Generate ID if not provided
            if (logEvent.getId() == null) {
                logEvent.setId(java.util.UUID.randomUUID().toString());
            }

            kafkaProducerService.sendLogEvent(logEvent);
            logCounter.increment();                                             // Metric Update

            log.info("Log event created successfully: {}", logEvent.getId());

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "id", logEvent.getId(),
                    "message", "Log event queued for processing"
            ));

        } catch (Exception e) {
            log.error("Failed to process log event", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Failed to process log event"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

}
