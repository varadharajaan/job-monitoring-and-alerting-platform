package com.jobmonitor.logingest.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for log alert patterns — regex patterns that trigger alerts
 * when matching log entries are ingested.
 */
@Entity
@Table(name = "log_alert_patterns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogAlertPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String regexPattern;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogEntry.LogLevel minLevel;

    @Column
    private String serviceFilter;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column
    private String notificationChannel;

    @Column
    private String notificationRecipient;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
