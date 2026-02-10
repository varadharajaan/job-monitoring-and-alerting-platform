package com.jobmonitor.platform.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;
import java.util.UUID;

/**
 * Request-scoped tenant context holder.
 * Populated by {@link JwtAuthenticationFilter} or a gateway header.
 * <p>
 * All downstream code accesses tenant via this bean — zero thread-local pollution.
 */
@Component
@RequestScope
@Getter
@Setter
public class TenantContext {

    private String tenantId;
    private String userId;
    private String username;

    /**
     * @return tenant ID wrapped in Optional — never null, never NPE.
     */
    public Optional<String> getTenantIdOptional() {
        return Optional.ofNullable(tenantId);
    }

    public Optional<UUID> getTenantUUID() {
        return Optional.ofNullable(tenantId)
                .filter(id -> !id.isBlank())
                .map(UUID::fromString);
    }

    public Optional<String> getUserIdOptional() {
        return Optional.ofNullable(userId);
    }
}
