package com.jobmonitor.auth.entity;

import com.jobmonitor.platform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * API Key entity — maps to V007 api_keys table.
 * Key stored as BCrypt hash; only key_prefix shown to users.
 */
@Entity
@Table(name = "api_keys", uniqueConstraints = {
        @UniqueConstraint(name = "uq_apikeys_name_tenant", columnNames = {"tenant_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
public class ApiKey extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "key_hash", nullable = false, length = 255)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 10)
    private String keyPrefix;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> scopes = List.of("READ");

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(nullable = false)
    private boolean enabled = true;
}
