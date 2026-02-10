package com.jobmonitor.jobqueue.service;

import com.jobmonitor.jobqueue.dto.QueueStatsResponse;
import com.jobmonitor.jobqueue.entity.JobQueueItem;
import com.jobmonitor.jobqueue.repository.JobQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for queue statistics and monitoring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueStatsService {

    private final JobQueueRepository queueRepository;

    @Transactional(readOnly = true)
    public QueueStatsResponse getStats(String tenantId) {
        var pending = queueRepository.countByTenantIdAndStatus(tenantId, JobQueueItem.Status.PENDING);
        var processing = queueRepository.countByTenantIdAndStatus(tenantId, JobQueueItem.Status.PROCESSING);
        var completed = queueRepository.countByTenantIdAndStatus(tenantId, JobQueueItem.Status.COMPLETED);
        var failed = queueRepository.countByTenantIdAndStatus(tenantId, JobQueueItem.Status.FAILED);
        var deadLetter = queueRepository.countByTenantIdAndStatus(tenantId, JobQueueItem.Status.DEAD_LETTER);
        var cancelled = queueRepository.countByTenantIdAndStatus(tenantId, JobQueueItem.Status.CANCELLED);
        var total = pending + processing + completed + failed + deadLetter + cancelled;

        double processingRate = total > 0 ? (double) completed / total * 100.0 : 0.0;

        return QueueStatsResponse.builder()
                .pendingCount(pending)
                .processingCount(processing)
                .completedCount(completed)
                .failedCount(failed)
                .deadLetterCount(deadLetter)
                .cancelledCount(cancelled)
                .totalCount(total)
                .processingRate(Math.round(processingRate * 100.0) / 100.0)
                .build();
    }
}
