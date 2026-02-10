package com.jobmonitor.logingest.scheduler;

import com.jobmonitor.logingest.model.LogRetentionPolicy;
import com.jobmonitor.logingest.service.LogRetentionService;
import com.jobmonitor.logingest.service.LogSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled job that enforces log retention policies by deleting
 * Elasticsearch indices older than the tenant's configured retention window.
 * Runs daily at 2:00 AM UTC.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LogRetentionScheduler {

    private final LogRetentionService retentionService;
    private final LogSearchService logSearchService;

    @Scheduled(cron = "0 0 2 * * *")
    public void enforceRetentionPolicies() {
        log.info("Starting log retention enforcement...");
        List<LogRetentionPolicy> policies = retentionService.getAllEnabled();

        int totalDeleted = 0;
        for (LogRetentionPolicy policy : policies) {
            try {
                int deleted = logSearchService.deleteOldIndices(
                        policy.getTenantId(), policy.getRetentionDays());
                totalDeleted += deleted;
                log.info("Retention enforced: tenant={}, retentionDays={}, deletedIndices={}",
                        policy.getTenantId(), policy.getRetentionDays(), deleted);
            } catch (Exception e) {
                log.error("Retention enforcement failed for tenant={}: {}",
                        policy.getTenantId(), e.getMessage(), e);
            }
        }
        log.info("Log retention enforcement complete: {} policies processed, {} indices deleted",
                policies.size(), totalDeleted);
    }
}
