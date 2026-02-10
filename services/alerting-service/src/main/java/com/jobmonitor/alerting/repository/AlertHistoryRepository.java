package com.jobmonitor.alerting.repository;

import com.jobmonitor.alerting.entity.AlertHistory;
import com.jobmonitor.alerting.entity.AlertHistoryId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for alert history (hypertable) — time-range partitioned queries.
 */
@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, AlertHistoryId> {

    Page<AlertHistory> findByTenantIdAndTriggeredAtBetween(String tenantId,
                                                            Instant from, Instant to,
                                                            Pageable pageable);

    Page<AlertHistory> findByAlertRuleIdAndTriggeredAtBetween(UUID ruleId,
                                                               Instant from, Instant to,
                                                               Pageable pageable);

    @Query("SELECT COUNT(h) FROM AlertHistory h WHERE h.alertRule.id = :ruleId AND h.triggeredAt > :since")
    long countByRuleIdSince(@Param("ruleId") UUID ruleId, @Param("since") Instant since);

    @Query("SELECT h FROM AlertHistory h WHERE h.tenantId = :tenantId AND h.status = :status " +
           "ORDER BY h.triggeredAt DESC")
    Page<AlertHistory> findByTenantIdAndStatus(@Param("tenantId") String tenantId,
                                                @Param("status") String status,
                                                Pageable pageable);

    Optional<AlertHistory> findFirstByAlertRuleIdOrderByTriggeredAtDesc(UUID ruleId);
}
