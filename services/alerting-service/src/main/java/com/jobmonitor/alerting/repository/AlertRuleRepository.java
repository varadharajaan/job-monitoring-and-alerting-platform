package com.jobmonitor.alerting.repository;

import com.jobmonitor.alerting.entity.AlertRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for alert rules — all lookups return Optional where single-row.
 */
@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    Optional<AlertRule> findByIdAndTenantId(UUID id, String tenantId);

    Optional<AlertRule> findByTenantIdAndName(String tenantId, String name);

    Page<AlertRule> findByTenantId(String tenantId, Pageable pageable);

    Page<AlertRule> findByTenantIdAndEnabled(String tenantId, Boolean enabled, Pageable pageable);

    @Query("SELECT r FROM AlertRule r WHERE r.tenantId = :tenantId AND r.jobId = :jobId AND r.enabled = true")
    List<AlertRule> findActiveRulesForJob(@Param("tenantId") String tenantId,
                                          @Param("jobId") UUID jobId);

    @Query("SELECT r FROM AlertRule r WHERE r.tenantId = :tenantId AND r.enabled = true AND r.ruleType = :ruleType")
    List<AlertRule> findActiveRulesByType(@Param("tenantId") String tenantId,
                                          @Param("ruleType") String ruleType);

    boolean existsByTenantIdAndName(String tenantId, String name);
}
