package com.jobmonitor.logingest.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for log retention policies — defines per-tenant retention
 * windows and Elasticsearch index lifecycle management.
 */
@Entity
@Table(name = "log_retention_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogRetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String tenantId;

    /** Number of days to retain logs in Elasticsearch. */
    @Column(nullable = false)
    @Builder.Default
    private int retentionDays = 30;

    /** Maximum storage size in GB before oldest logs are pruned. */
    @Column(nullable = false)
    @Builder.Default
    private long maxStorageGb = 10;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
