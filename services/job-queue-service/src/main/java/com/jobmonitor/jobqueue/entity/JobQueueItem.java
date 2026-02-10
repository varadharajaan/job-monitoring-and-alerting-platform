package com.jobmonitor.jobqueue.entity;

import com.jobmonitor.platform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Job queue item entity — maps to V006 job_queue table.
 */
@Entity
@Table(name = "job_queue")
@Getter
@Setter
@NoArgsConstructor
public class JobQueueItem extends BaseEntity {

    @Column(name = "job_type", nullable = false, length = 100)
    private String jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @Column(nullable = false)
    private int priority = 5;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "assigned_worker", length = 255)
    private String assignedWorker;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public enum Status { PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, DEAD_LETTER }
}
