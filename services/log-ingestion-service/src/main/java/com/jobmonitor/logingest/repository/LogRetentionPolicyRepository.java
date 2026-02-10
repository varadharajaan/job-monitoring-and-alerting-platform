package com.jobmonitor.logingest.repository;

import com.jobmonitor.logingest.model.LogRetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LogRetentionPolicyRepository extends JpaRepository<LogRetentionPolicy, UUID> {

    Optional<LogRetentionPolicy> findByTenantId(String tenantId);
}
