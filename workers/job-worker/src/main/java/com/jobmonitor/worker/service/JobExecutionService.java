package com.jobmonitor.worker.service;

import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.AlertEvent;
import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.platform.common.functional.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Job execution service — handles each job event action.
 * Publishes alert events when failures/SLA violations are detected.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobExecutionService {

    private final EventPublisher eventPublisher;
    private final PlatformProperties platformProperties;

    private String getAlertTopic() {
        return Optional.ofNullable(platformProperties.getKafka())
                .map(PlatformProperties.KafkaConfig::getTopics)
                .map(PlatformProperties.KafkaConfig.Topics::getAlertEvents)
                .orElse("job-monitor.alert-events");
    }

    public void handleJobRegistered(JobEvent event) {
        log.info("Job registered: name={}, tenant={}", event.getJobName(), event.getTenantId());
    }

    public void handleJobStarted(JobEvent event) {
        log.info("Job started: name={}, executionId={}", event.getJobName(), event.getExecutionId());
    }

    public void handleJobCompleted(JobEvent event) {
        log.info("Job completed: name={}, exitCode={}", event.getJobName(), event.getExitCode());
    }

    public void handleJobFailed(JobEvent event) {
        log.warn("Job FAILED: name={}, error={}", event.getJobName(), event.getErrorMessage());
        publishAlertEvent(event, "FAILURE", "Job failed: " + event.getJobName());
    }

    public void handleSlaViolation(JobEvent event) {
        log.warn("SLA VIOLATED: name={}", event.getJobName());
        publishAlertEvent(event, "SLA_BREACH", "SLA violated for: " + event.getJobName());
    }

    public void handleJobRetry(JobEvent event) {
        log.info("Job retrying: name={}", event.getJobName());
    }

    public void handleHeartbeatMissed(JobEvent event) {
        log.warn("Heartbeat MISSED: name={}", event.getJobName());
        publishAlertEvent(event, "HEARTBEAT_MISSED", "Heartbeat missed for: " + event.getJobName());
    }

    private void publishAlertEvent(JobEvent event, String alertType, String message) {
        var alertEvent = AlertEvent.builder()
                .tenantId(event.getTenantId())
                .action(AlertEvent.Action.TRIGGERED)
                .severity(AlertEvent.Severity.HIGH)
                .alertName(alertType + ": " + event.getJobName())
                .description(message)
                .context(Map.of(
                        "exitCode", String.valueOf(event.getExitCode()),
                        "errorMessage", Optional.ofNullable(event.getErrorMessage()).orElse(""),
                        "jobName", Optional.ofNullable(event.getJobName()).orElse("")
                ))
                .build();

        eventPublisher.publish(getAlertTopic(),
                event.getTenantId() + ":" + event.getJobName(), alertEvent);
    }
}
