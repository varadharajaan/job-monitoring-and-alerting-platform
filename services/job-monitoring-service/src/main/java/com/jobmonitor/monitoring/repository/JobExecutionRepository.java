package com.jobmonitor.monitoring.repository;

import com.jobmonitor.monitoring.entity.JobExecution;
import com.jobmonitor.monitoring.entity.JobExecutionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA repository for {@link JobExecution} entity (TimescaleDB hypertable).
 */
@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, JobExecutionId> {

    Page<JobExecution> findByJobIdAndTenantId(UUID jobId, String tenantId, Pageable pageable);

    Page<JobExecution> findByTenantId(String tenantId, Pageable pageable);

    @Query("SELECT je FROM JobExecution je WHERE je.job.id = :jobId AND je.tenantId = :tenantId " +
           "AND je.startedAt BETWEEN :from AND :to ORDER BY je.startedAt DESC")
    List<JobExecution> findByJobIdAndTimeRange(@Param("jobId") UUID jobId,
                                                @Param("tenantId") String tenantId,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to);

    @Query("SELECT COUNT(je) FROM JobExecution je WHERE je.job.id = :jobId " +
           "AND je.status = :status AND je.startedAt >= :since")
    long countByJobIdAndStatusSince(@Param("jobId") UUID jobId,
                                    @Param("status") String status,
                                    @Param("since") Instant since);

    @Query("SELECT je FROM JobExecution je WHERE je.job.id = :jobId " +
           "ORDER BY je.startedAt DESC")
    Page<JobExecution> findLatestByJobId(@Param("jobId") UUID jobId, Pageable pageable);
}
