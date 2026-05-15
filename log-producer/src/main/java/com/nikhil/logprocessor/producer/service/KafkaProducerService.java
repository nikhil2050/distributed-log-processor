package com.nikhil.logprocessor.producer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikhil.logprocessor.producer.model.LogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;

@Service
@Slf4j
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.kafka.topic.log-events:log-events}")
    private String logEventsTopic;

    public void sendLogEvent(LogEvent logEvent) {
        try {
            String logEventStr = objectMapper.writeValueAsString(logEvent);

            // Use organizationId as partition key for ordering
            String partitionKey = logEvent.getOrganizationId();

            ListenableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(logEventsTopic, partitionKey, logEventStr);
            
            future.addCallback(
                    result -> {
                        log.info("Successfully sent log event to Kafka: {} - Topic: {}, Partition: {}, Offset: {}",
                                logEvent.getId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                     },
                    ex -> log.error("Failed to send log event to Kafka: {}", logEvent.getId(), ex)
            );
        } catch (JsonProcessingException e) {
            log.error("Error serializing log event: {}", logEvent.getId(), e);
            throw new RuntimeException("Failed to send log event", e);
        }
    }
}



