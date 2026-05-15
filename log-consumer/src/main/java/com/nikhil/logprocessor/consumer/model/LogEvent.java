package com.nikhil.logprocessor.consumer.model;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "log_events")
@Data
public class LogEvent {

    @Id
    private String id;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "level", nullable = false)
    private String level;                       // INFO, WARN, ERROR

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "source")
    private String source;                      // Which application sent this

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

//    // Constructor that auto-generates ID and timestamp
//    public LogEvent() {
//        this.timestamp = LocalDateTime.now();
//        this.id = java.util.UUID.randomUUID().toString();
//    }


}
