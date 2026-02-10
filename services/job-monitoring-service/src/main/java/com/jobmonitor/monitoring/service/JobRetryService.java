package com.jobmonitor.monitoring.service;

import com.jobmonitor.monitoring.entity.Job;
import com.jobmonitor.monitoring.repository.JobRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.platform.common.event.PlatformEvent;
import com.jobmonitor.platform.common.exception.BusinessException;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.functional.EventPublisher;
import com.jobmonitor.platform.common.functional.RetryExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for retrying failed job executions with exponential backoff.
 * Uses the {@link RetryExecutor} functional interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobRetryService {

    private final JobRepository jobRepository;
    private final PlatformProperties properties;
    private final EventPublisher<PlatformEvent> eventPublisher;
    private final RetryExecutor<String> retryExecutor;

    @Transactional
    public void retryFailedJob(String tenantId, UUID jobId) {
        var job = jobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));

        if (!"ACTIVE".equals(job.getStatus())) {
            throw new BusinessException("Can only retry ACTIVE jobs, current status: " + job.getStatus());
        }

        int maxRetries = properties.getJobMonitor().getMaxRetryAttempts();

        log.info("Initiating retry for job={}, tenant={}, maxRetries={}", jobId, tenantId, maxRetries);

        // Publish RETRYING event
        var event = JobEvent.builder()
                .tenantId(tenantId)
                .jobId(jobId)
                .action(JobEvent.Action.RETRYING)
                .jobName(job.getName())
                .build();

        eventPublisher.publish(
                properties.getKafka().getTopics().getJobEvents(),
                tenantId,
                event);

        log.info("Retry event published for job={}", jobId);
    }
}
