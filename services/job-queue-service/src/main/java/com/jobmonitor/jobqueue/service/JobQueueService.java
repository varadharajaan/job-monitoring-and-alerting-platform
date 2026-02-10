package com.jobmonitor.jobqueue.service;

import com.jobmonitor.jobqueue.dto.EnqueueRequest;
import com.jobmonitor.jobqueue.dto.QueueItemResponse;
import com.jobmonitor.jobqueue.entity.JobQueueItem;
import com.jobmonitor.jobqueue.repository.JobQueueRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.QueueEvent;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.functional.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Job queue service — enqueue, claim, complete, fail with distributed locking.
 * Uses SQL SELECT ... FOR UPDATE SKIP LOCKED for atomic claiming.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobQueueService {

    private final JobQueueRepository queueRepository;
    private final EventPublisher eventPublisher;
    private final PlatformProperties platformProperties;

    /** Topic resolved from config */
    private String getQueueTopic() {
        return Optional.ofNullable(platformProperties.getKafka())
                .map(PlatformProperties.KafkaConfig::getTopics)
                .map(PlatformProperties.KafkaConfig.Topics::getQueueEvents)
                .orElse("job-monitor.queue-events");
}

    /** Entity to response mapper */
    private final Function<JobQueueItem, QueueItemResponse> toResponse = item ->
            QueueItemResponse.builder()
                    .id(item.getId())
                    .tenantId(item.getTenantId())
                    .jobType(item.getJobType())
                    .payload(item.getPayload())
                    .priority(item.getPriority())
                    .status(item.getStatus().name())
                    .assignedWorker(item.getAssignedWorker())
                    .attemptCount(item.getAttemptCount())
                    .maxAttempts(item.getMaxAttempts())
                    .scheduledAt(item.getScheduledAt())
                    .startedAt(item.getStartedAt())
                    .completedAt(item.getCompletedAt())
                    .errorMessage(item.getErrorMessage())
                    .createdAt(item.getCreatedAt())
                    .build();

    @Transactional
    public QueueItemResponse enqueue(String tenantId, EnqueueRequest request) {
        var item = new JobQueueItem();
        item.setTenantId(tenantId);
        item.setJobType(request.getJobType());
        item.setPayload(request.getPayload());
        item.setPriority(request.getPriority());
        item.setMaxAttempts(request.getMaxAttempts());
        item.setScheduledAt(request.getScheduledAt());

        var saved = queueRepository.save(item);
        publishEvent(saved, QueueEvent.Action.ENQUEUED);
        log.info("Enqueued job: type={}, priority={}, tenant={}", saved.getJobType(), saved.getPriority(), tenantId);
        return toResponse.apply(saved);
    }

    /**
     * Atomic claim — next available item locked and assigned to worker.
     * Uses SELECT ... FOR UPDATE SKIP LOCKED.
     */
    @Transactional
    public Optional<QueueItemResponse> claimNext(String workerId) {
        return queueRepository.findNextAvailableItem(Instant.now())
                .map(item -> {
                    item.setStatus(JobQueueItem.Status.PROCESSING);
                    item.setAssignedWorker(workerId);
                    item.setStartedAt(Instant.now());
                    item.setAttemptCount(item.getAttemptCount() + 1);
                    item.setLockedUntil(Instant.now().plusSeconds(
                            platformProperties.getJobQueue().getLockTimeout().getSeconds()));
                    var saved = queueRepository.save(item);
                    publishEvent(saved, QueueEvent.Action.PROCESSING);
                    log.info("Claimed job: id={}, worker={}", saved.getId(), workerId);
                    return toResponse.apply(saved);
                });
    }

    @Transactional
    public QueueItemResponse complete(UUID itemId) {
        return queueRepository.findById(itemId)
                .map(item -> {
                    item.setStatus(JobQueueItem.Status.COMPLETED);
                    item.setCompletedAt(Instant.now());
                    item.setLockedUntil(null);
                    var saved = queueRepository.save(item);
                    publishEvent(saved, QueueEvent.Action.COMPLETED);
                    return toResponse.apply(saved);
                })
                .orElseThrow(() -> new ResourceNotFoundException("QueueItem", itemId.toString()));
    }

    @Transactional
    public QueueItemResponse fail(UUID itemId, String errorMessage) {
        return queueRepository.findById(itemId)
                .map(item -> {
                    item.setErrorMessage(errorMessage);
                    item.setLockedUntil(null);
                    if (item.getAttemptCount() >= item.getMaxAttempts()) {
                        item.setStatus(JobQueueItem.Status.DEAD_LETTER);
                        publishEvent(item, QueueEvent.Action.FAILED);
                    } else {
                        item.setStatus(JobQueueItem.Status.PENDING);
                        publishEvent(item, QueueEvent.Action.RETRYING);
                    }
                    return toResponse.apply(queueRepository.save(item));
                })
                .orElseThrow(() -> new ResourceNotFoundException("QueueItem", itemId.toString()));
    }

    @Transactional
    public void cancel(UUID itemId) {
        queueRepository.findById(itemId).ifPresentOrElse(
                item -> {
                    item.setStatus(JobQueueItem.Status.CANCELLED);
                    item.setLockedUntil(null);
                    queueRepository.save(item);
                    publishEvent(item, QueueEvent.Action.CANCELLED);
                },
                () -> { throw new ResourceNotFoundException("QueueItem", itemId.toString()); }
        );
    }

    @Transactional(readOnly = true)
    public Page<QueueItemResponse> listByTenant(String tenantId, Pageable pageable) {
        return queueRepository.findByTenantIdOrderByPriorityAscCreatedAtAsc(tenantId, pageable)
                .map(toResponse);
    }

    @Transactional(readOnly = true)
    public Page<QueueItemResponse> listByStatus(String tenantId, String status, Pageable pageable) {
        var statusEnum = JobQueueItem.Status.valueOf(status.toUpperCase());
        return queueRepository.findByTenantIdAndStatus(tenantId, statusEnum, pageable)
                .map(toResponse);
    }

    private void publishEvent(JobQueueItem item, QueueEvent.Action action) {
        var event = QueueEvent.builder()
                .tenantId(item.getTenantId())
                .queuedJobId(item.getId())
                .action(action)
                .jobType(item.getJobType())
                .priority(item.getPriority())
                .attemptNumber(item.getAttemptCount())
                .workerNodeId(item.getAssignedWorker())
                .errorMessage(item.getErrorMessage())
                .payload(item.getPayload())
                .build();
        eventPublisher.publish(getQueueTopic(), item.getTenantId() + ":" + item.getId(), event);
    }
}
