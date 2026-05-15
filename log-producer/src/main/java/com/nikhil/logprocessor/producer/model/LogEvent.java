package com.nikhil.logprocessor.producer.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LogEvent {

    private String id;
    private String organizationId;
    private String level;        // INFO, WARN, ERROR
    private String message;
    private String source;       // Which application sent this
    private LocalDateTime timestamp;

    // Constructor that auto-generates ID and timestamp
    public LogEvent() {
        this.timestamp = LocalDateTime.now();
        this.id = java.util.UUID.randomUUID().toString();

    }


}
