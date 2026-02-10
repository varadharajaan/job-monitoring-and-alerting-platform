package com.jobmonitor.monitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for job execution records.
 * Maps to the 'job_executions' hypertable (V003).
 * Composite PK on (id, started_at) for TimescaleDB partitioning.
 */
@Entity
@Table(name = "job_executions")
@IdClass(JobExecutionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecution {

    @Id
    @Column(updatable = false, nullable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Id
    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
