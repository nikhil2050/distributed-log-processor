package com.nikhil.logprocessor.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikhil.logprocessor.consumer.model.LogEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaConsumerService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LogEventService logEventService;

    private final Counter processedCounter;
    private final Counter errorCounter;

    public KafkaConsumerService(MeterRegistry meterRegistry) {
        this.processedCounter = Counter.builder("log_events_processed_total")
                .description("Total number of log events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("log_events_errors_total")
                .description("Total number of log event processing errors")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "log-events", groupId = "log-consumer-group")
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    public void consume(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            Acknowledgment acknowledgment) {

        try {
            log.debug("Received message from topic: {}, partition: {}", topic, partition);

            LogEvent logEvent = objectMapper.readValue(message, LogEvent.class);
            
            // Call the processing service - has @Retryable annotation
            // Automatically retries on database exceptions
            logEventService.processLogEvent(logEvent);

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.debug("Successfully processed log event: {}", logEvent.getId());

        } catch (Exception e) {
            log.error("Failed to process message after all retries: {}", message, e);
            errorCounter.increment();
            throw new RuntimeException("Failed to process log event", e);
        }
    }
}
