package com.jobmonitor.logingest.model;

import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a single log entry stored in Elasticsearch and consumed from Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    private String id;
    private String tenantId;
    private String source;
    private String hostname;
    private String service;
    private LogLevel level;
    private String message;
    private String logger;
    private String threadName;
    private String stackTrace;
    private Map<String, String> metadata;
    private Instant timestamp;

    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }
}
