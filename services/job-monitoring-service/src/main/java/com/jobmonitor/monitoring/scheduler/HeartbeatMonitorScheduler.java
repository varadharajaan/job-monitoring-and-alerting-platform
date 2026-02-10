package com.jobmonitor.monitoring.scheduler;

import com.jobmonitor.monitoring.entity.Job;
import com.jobmonitor.monitoring.repository.JobExecutionRepository;
import com.jobmonitor.monitoring.repository.JobRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.platform.common.event.PlatformEvent;
import com.jobmonitor.platform.common.functional.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Heartbeat monitor scheduled task.
 * Checks each active job for missing heartbeats — if the last execution
 * was too long ago compared to the expected schedule interval,
 * publishes a HEARTBEAT_MISSED event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatMonitorScheduler {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final PlatformProperties properties;
    private final EventPublisher<PlatformEvent> eventPublisher;

    /** Predicate: job has a cron expression (i.e., is scheduled) */
    private final Predicate<Job> isScheduled = job ->
            Optional.ofNullable(job.getCronExpression())
                    .filter(c -> !c.isBlank())
                    .isPresent();

    @Scheduled(fixedDelayString = "${platform.job-monitor.health-check-interval-ms:60000}")
    @Transactional(readOnly = true)
    public void checkHeartbeats() {
        log.debug("Running heartbeat check");

        var activeJobs = jobRepository.findAll().stream()
                .filter(job -> "ACTIVE".equals(job.getStatus()))
                .filter(isScheduled)
                .toList();

        int missed = 0;
        for (var job : activeJobs) {
            var gracePeriod = Optional.ofNullable(job.getGracePeriodSeconds()).orElse(300);
            var expectedRuntime = Optional.ofNullable(job.getExpectedRuntimeSeconds()).orElse(3600);

            // Check if last execution is too old
            var latestPage = executionRepository.findLatestByJobId(job.getId(), PageRequest.of(0, 1));
            if (latestPage.hasContent()) {
                var latest = latestPage.getContent().get(0);
                var lastTime = Optional.ofNullable(latest.getCompletedAt())
                        .orElse(latest.getStartedAt());

                if (lastTime != null) {
                    var silenceDuration = java.time.Duration.between(lastTime, Instant.now());
                    var threshold = java.time.Duration.ofSeconds(expectedRuntime + gracePeriod);

                    if (silenceDuration.compareTo(threshold) > 0) {
                        publishHeartbeatMissed(job, silenceDuration.getSeconds());
                        missed++;
                    }
                }
            }
        }

        if (missed > 0) {
            log.warn("Heartbeat check: {} missed heartbeats detected", missed);
        }
    }

    private void publishHeartbeatMissed(Job job, long silenceSeconds) {
        var event = JobEvent.builder()
                .tenantId(job.getTenantId())
                .jobId(job.getId())
                .action(JobEvent.Action.HEARTBEAT_MISSED)
                .jobName(job.getName())
                .cronExpression(job.getCronExpression())
                .build();

        eventPublisher.publish(
                properties.getKafka().getTopics().getJobEvents(),
                job.getTenantId(),
                event);

        log.warn("Heartbeat missed: job={}, silent for {}s", job.getName(), silenceSeconds);
    }
}
