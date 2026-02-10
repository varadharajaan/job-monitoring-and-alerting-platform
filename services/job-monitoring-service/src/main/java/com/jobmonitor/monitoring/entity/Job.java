package com.jobmonitor.monitoring.entity;

import com.jobmonitor.platform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA entity representing a registered monitored job.
 * Maps to the 'jobs' table created by V002 Flyway migration.
 */
@Entity
@Table(name = "jobs", uniqueConstraints = {
    @UniqueConstraint(name = "uq_jobs_tenant_name", columnNames = {"tenant_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(name = "schedule_type", nullable = false, length = 50)
    @Builder.Default
    private String scheduleType = "CRON";

    @Column(name = "sla_seconds")
    private Integer slaSeconds;

    @Column(name = "grace_period_seconds")
    @Builder.Default
    private Integer gracePeriodSeconds = 300;

    @Column(name = "expected_runtime_seconds")
    private Integer expectedRuntimeSeconds;

    @Column(name = "timeout_seconds")
    @Builder.Default
    private Integer timeoutSeconds = 3600;

    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<JobExecution> executions = new ArrayList<>();
}
