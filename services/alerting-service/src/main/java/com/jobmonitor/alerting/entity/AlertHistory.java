package com.jobmonitor.alerting.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for the alert_history hypertable (V004).
 * Composite PK on (id, triggeredAt) for TimescaleDB partitioning.
 */
@Entity
@Table(name = "alert_history")
@IdClass(AlertHistoryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Id
    @Column(name = "triggered_at", nullable = false)
    @Builder.Default
    private Instant triggeredAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_rule_id", nullable = false)
    private AlertRule alertRule;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "TRIGGERED";

    @Column(columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> contextJson = Map.of();

    @Column(name = "acknowledged_by", length = 255)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
