package com.jobmonitor.alerting.entity;

import com.jobmonitor.platform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing an alert rule configuration.
 * Maps to V004 'alert_rules' table.
 */
@Entity
@Table(name = "alert_rules", uniqueConstraints = {
    @UniqueConstraint(name = "uq_alert_rule_tenant_name", columnNames = {"tenant_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "job_id")
    private java.util.UUID jobId;

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_json", columnDefinition = "jsonb", nullable = false)
    private String conditionJson;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String severity = "MEDIUM";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_channels", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> notificationChannels = new ArrayList<>(List.of("EMAIL"));

    @Column(name = "cooldown_seconds")
    @Builder.Default
    private Integer cooldownSeconds = 300;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
