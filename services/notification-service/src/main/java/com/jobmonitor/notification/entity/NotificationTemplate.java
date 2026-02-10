package com.jobmonitor.notification.entity;

import com.jobmonitor.platform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for notification_templates (V005).
 * Unique per (tenant_id, name, channel).
 */
@Entity
@Table(name = "notification_templates", uniqueConstraints = {
    @UniqueConstraint(name = "uq_template_tenant_name_channel",
                      columnNames = {"tenant_id", "name", "channel"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(length = 500)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> variables = new ArrayList<>();
}
