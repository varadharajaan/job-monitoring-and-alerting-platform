package com.jobmonitor.auth.repository;

import com.jobmonitor.auth.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyPrefixAndEnabledTrue(String keyPrefix);

    List<ApiKey> findByUserIdAndEnabledTrue(UUID userId);

    List<ApiKey> findByTenantId(String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);
}
