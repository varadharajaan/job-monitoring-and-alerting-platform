package com.jobmonitor.jobqueue.repository;

import com.jobmonitor.jobqueue.entity.JobQueueItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobQueueRepository extends JpaRepository<JobQueueItem, UUID> {

    Page<JobQueueItem> findByTenantIdOrderByPriorityAscCreatedAtAsc(String tenantId, Pageable pageable);

    Page<JobQueueItem> findByTenantIdAndStatus(String tenantId, JobQueueItem.Status status, Pageable pageable);

    /**
     * Atomic claim: picks next available item and assigns it to a worker.
     * Uses SELECT ... FOR UPDATE SKIP LOCKED for distributed safety.
     */
    @Query(value = """
        SELECT * FROM job_queue
        WHERE status = 'PENDING'
          AND (scheduled_at IS NULL OR scheduled_at <= :now)
          AND (locked_until IS NULL OR locked_until < :now)
        ORDER BY priority ASC, created_at ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<JobQueueItem> findNextAvailableItem(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE JobQueueItem j SET j.status = 'DEAD_LETTER' " +
           "WHERE j.status = 'FAILED' AND j.attemptCount >= j.maxAttempts")
    int moveFailedToDeadLetter();

    List<JobQueueItem> findByStatusAndLockedUntilBefore(JobQueueItem.Status status, Instant now);

    long countByTenantIdAndStatus(String tenantId, JobQueueItem.Status status);
}
