package com.jobmonitor.logingest.repository;

import com.jobmonitor.logingest.model.LogAlertPattern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LogAlertPatternRepository extends JpaRepository<LogAlertPattern, UUID> {

    List<LogAlertPattern> findByTenantIdAndEnabledTrue(String tenantId);

    List<LogAlertPattern> findByEnabledTrue();
}
