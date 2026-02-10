package com.jobmonitor.dbperf.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a monitored database connection target.
 */
@Entity
@Table(name = "monitored_databases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoredDatabase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String jdbcUrl;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String encryptedPassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DatabaseType dbType = DatabaseType.POSTGRESQL;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Minimum execution time (ms) to capture a query as "slow". */
    @Column(nullable = false)
    @Builder.Default
    private long slowQueryThresholdMs = 1000;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    public enum DatabaseType {
        POSTGRESQL, MYSQL
    }
}
