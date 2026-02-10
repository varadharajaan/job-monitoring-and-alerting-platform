package com.jobmonitor.dbperf.repository;

import com.jobmonitor.dbperf.model.MonitoredDatabase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MonitoredDatabaseRepository extends JpaRepository<MonitoredDatabase, UUID> {

    List<MonitoredDatabase> findByTenantIdAndEnabledTrue(String tenantId);

    List<MonitoredDatabase> findByEnabledTrue();
}
