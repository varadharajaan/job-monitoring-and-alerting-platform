package com.jobmonitor.jobqueue.scheduler;

import com.jobmonitor.jobqueue.entity.JobQueueItem;
import com.jobmonitor.jobqueue.repository.JobQueueRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled task to clean up timed-out queue items.
 * <p>
 * Items that are in PROCESSING status with an expired {@code lockedUntil}
 * are released back to PENDING for re-processing, or moved to DEAD_LETTER
 * if max attempts have been reached.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueTimeoutCleanupScheduler {

    private final JobQueueRepository queueRepository;
    private final PlatformProperties properties;

    @Scheduled(fixedDelayString = "${platform.job-queue.poll-interval-ms:5000}")
    @Transactional
    public void cleanupTimedOutItems() {
        var timedOut = queueRepository.findByStatusAndLockedUntilBefore(
                JobQueueItem.Status.PROCESSING, Instant.now());

        if (timedOut.isEmpty()) {
            return;
        }

        int released = 0;
        int deadLettered = 0;

        for (var item : timedOut) {
            if (item.getAttemptCount() >= item.getMaxAttempts()) {
                item.setStatus(JobQueueItem.Status.DEAD_LETTER);
                item.setErrorMessage("Timed out after " + item.getAttemptCount() + " attempts");
                item.setLockedUntil(null);
                queueRepository.save(item);
                deadLettered++;
            } else {
                item.setStatus(JobQueueItem.Status.PENDING);
                item.setAssignedWorker(null);
                item.setLockedUntil(null);
                queueRepository.save(item);
                released++;
            }
        }

        log.info("Queue timeout cleanup: {} released back to PENDING, {} moved to DEAD_LETTER",
                released, deadLettered);
    }
}
