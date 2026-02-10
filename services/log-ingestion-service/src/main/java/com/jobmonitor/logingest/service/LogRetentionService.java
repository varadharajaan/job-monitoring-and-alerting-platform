package com.jobmonitor.logingest.service;

import com.jobmonitor.logingest.model.LogRetentionPolicy;
import com.jobmonitor.logingest.repository.LogRetentionPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages per-tenant log retention policies and coordinates with
 * {@link LogRetentionScheduler} for periodic index cleanup.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogRetentionService {

    private final LogRetentionPolicyRepository repository;

    public LogRetentionPolicy createOrUpdate(LogRetentionPolicy policy) {
        return repository.findByTenantId(policy.getTenantId())
                .map(existing -> {
                    existing.setRetentionDays(policy.getRetentionDays());
                    existing.setMaxStorageGb(policy.getMaxStorageGb());
                    existing.setEnabled(policy.isEnabled());
                    return repository.save(existing);
                })
                .orElseGet(() -> repository.save(policy));
    }

    public Optional<LogRetentionPolicy> getForTenant(String tenantId) {
        return repository.findByTenantId(tenantId);
    }

    public List<LogRetentionPolicy> getAllEnabled() {
        return repository.findAll().stream()
                .filter(LogRetentionPolicy::isEnabled)
                .toList();
    }

    public void deletePolicy(UUID id) {
        repository.deleteById(id);
    }
}
