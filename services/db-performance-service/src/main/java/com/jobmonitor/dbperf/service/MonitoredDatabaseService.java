package com.jobmonitor.dbperf.service;

import com.jobmonitor.dbperf.model.MonitoredDatabase;
import com.jobmonitor.dbperf.repository.MonitoredDatabaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD service for monitored database connections.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MonitoredDatabaseService {

    private final MonitoredDatabaseRepository repository;

    public MonitoredDatabase register(MonitoredDatabase db) {
        log.info("Registering monitored database: name={}, tenant={}", db.getName(), db.getTenantId());
        return repository.save(db);
    }

    public Optional<MonitoredDatabase> getById(UUID id) {
        return repository.findById(id);
    }

    public List<MonitoredDatabase> getForTenant(String tenantId) {
        return repository.findByTenantIdAndEnabledTrue(tenantId);
    }

    public List<MonitoredDatabase> getAllEnabled() {
        return repository.findByEnabledTrue();
    }

    public MonitoredDatabase update(UUID id, MonitoredDatabase updated) {
        return repository.findById(id)
                .map(existing -> {
                    if (updated.getName() != null) existing.setName(updated.getName());
                    if (updated.getJdbcUrl() != null) existing.setJdbcUrl(updated.getJdbcUrl());
                    if (updated.getUsername() != null) existing.setUsername(updated.getUsername());
                    if (updated.getEncryptedPassword() != null) existing.setEncryptedPassword(updated.getEncryptedPassword());
                    if (updated.getDbType() != null) existing.setDbType(updated.getDbType());
                    existing.setSlowQueryThresholdMs(updated.getSlowQueryThresholdMs());
                    existing.setEnabled(updated.isEnabled());
                    return repository.save(existing);
                })
                .orElseThrow(() -> new IllegalArgumentException("Database not found: " + id));
    }

    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
