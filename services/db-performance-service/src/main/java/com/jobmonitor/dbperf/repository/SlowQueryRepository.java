package com.jobmonitor.dbperf.repository;

import com.jobmonitor.dbperf.model.SlowQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlowQueryRepository extends JpaRepository<SlowQuery, UUID> {

    Page<SlowQuery> findByDatabaseIdOrderByMeanTimeMsDesc(UUID databaseId, Pageable pageable);

    List<SlowQuery> findByTenantIdOrderByMeanTimeMsDesc(String tenantId);

    List<SlowQuery> findByDatabaseIdAndQueryFingerprint(UUID databaseId, String fingerprint);
}
