package com.jobmonitor.monitoring.repository;

import com.jobmonitor.monitoring.entity.Job;
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
 * JPA repository for {@link Job} entity.
 * Query methods return Optional where a single result is expected.
 */
@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByIdAndTenantId(UUID id, String tenantId);

    Page<Job> findByTenantId(String tenantId, Pageable pageable);

    Page<Job> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    Optional<Job> findByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndName(String tenantId, String name);

    @Query("SELECT j FROM Job j WHERE j.tenantId = :tenantId AND j.status = :status ORDER BY j.createdAt DESC")
    List<Job> findActiveJobsByTenant(@Param("tenantId") String tenantId,
                                     @Param("status") String status);

    @Query("SELECT j FROM Job j WHERE j.tenantId = :tenantId AND :tag MEMBER OF j.tags")
    Page<Job> findByTenantIdAndTag(@Param("tenantId") String tenantId,
                                    @Param("tag") String tag,
                                    Pageable pageable);
}
