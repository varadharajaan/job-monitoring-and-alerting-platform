package com.jobmonitor.monitoring.scheduler;

import com.jobmonitor.monitoring.entity.Job;
import com.jobmonitor.monitoring.entity.JobExecution;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Scheduled SLA evaluation task.
 * Runs on a cron defined in {@code platform.job-monitor.sla-evaluation-cron}.
 * <p>
 * For each active job with an SLA, checks the latest execution to see if
 * it exceeded the SLA duration. If so, publishes a SLA_VIOLATED event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaEvaluationScheduler {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final PlatformProperties properties;
    private final EventPublisher<PlatformEvent> eventPublisher;

    /** Predicate: job has an SLA configured */
    private final Predicate<Job> hasSla = job ->
            Optional.ofNullable(job.getSlaSeconds()).filter(s -> s > 0).isPresent();

    /** Predicate: execution exceeded SLA */
    private Predicate<JobExecution> exceedsSla(int slaSeconds) {
        return execution -> Optional.ofNullable(execution.getDurationMs())
                .filter(ms -> ms > slaSeconds * 1000L)
                .isPresent();
    }

    @Scheduled(cron = "${platform.job-monitor.sla-evaluation-cron:0 */5 * * * *}")
    @Transactional(readOnly = true)
    public void evaluateSlaCompliance() {
        log.debug("Running SLA evaluation cycle");

        // Collect all distinct tenants with active jobs
        var activeJobs = jobRepository.findAll().stream()
                .filter(job -> "ACTIVE".equals(job.getStatus()))
                .filter(hasSla)
                .toList();

        int violations = 0;
        for (var job : activeJobs) {
            var latestPage = executionRepository.findLatestByJobId(job.getId(), PageRequest.of(0, 1));
            if (latestPage.hasContent()) {
                var latest = latestPage.getContent().get(0);
                if (exceedsSla(job.getSlaSeconds()).test(latest)) {
                    publishSlaViolation(job, latest);
                    violations++;
                }
            }
        }

        if (violations > 0) {
            log.info("SLA evaluation complete: {} violations detected out of {} jobs checked",
                    violations, activeJobs.size());
        } else {
            log.debug("SLA evaluation complete: 0 violations, {} jobs checked", activeJobs.size());
        }
    }

    private void publishSlaViolation(Job job, JobExecution execution) {
        var event = JobEvent.builder()
                .tenantId(job.getTenantId())
                .jobId(job.getId())
                .executionId(execution.getId())
                .action(JobEvent.Action.SLA_VIOLATED)
                .jobName(job.getName())
                .executionStartTime(execution.getStartedAt())
                .executionEndTime(execution.getCompletedAt())
                .build();

        eventPublisher.publish(
                properties.getKafka().getTopics().getJobEvents(),
                job.getTenantId(),
                event);

        log.warn("SLA violation: job={}, sla={}s, actual={}ms",
                job.getName(), job.getSlaSeconds(), execution.getDurationMs());
    }
}
