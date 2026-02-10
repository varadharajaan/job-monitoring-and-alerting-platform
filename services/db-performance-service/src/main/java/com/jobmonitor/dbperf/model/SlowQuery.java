package com.jobmonitor.dbperf.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a captured slow query from a monitored database.
 */
@Entity
@Table(name = "slow_queries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlowQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID databaseId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String queryText;

    /** Normalized query fingerprint for grouping identical query patterns. */
    @Column(nullable = false)
    private String queryFingerprint;

    /** Average execution time in milliseconds. */
    @Column(nullable = false)
    private double meanTimeMs;

    /** Maximum execution time in milliseconds. */
    @Column(nullable = false)
    private double maxTimeMs;

    /** Total number of times this query was called. */
    @Column(nullable = false)
    private long calls;

    /** Total rows returned/affected. */
    @Column(nullable = false)
    private long totalRows;

    /** Shared buffer hits. */
    @Column
    private long sharedBlksHit;

    /** Shared buffer reads (disk I/O). */
    @Column
    private long sharedBlksRead;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant capturedAt;
}
