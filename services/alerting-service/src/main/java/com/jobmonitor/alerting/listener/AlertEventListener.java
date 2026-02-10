package com.jobmonitor.alerting.listener;

import com.jobmonitor.alerting.service.AlertService;
import com.jobmonitor.platform.common.event.JobEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Kafka consumer for job events — triggers alert evaluation in real-time.
 * <p>
 * Listens on the job-events topic and builds evaluation context from each event,
 * then delegates to {@link AlertService#evaluateRules} for functional dispatch.
 * <p>
 * This replaces the previous REST-only alert evaluation approach, enabling
 * event-driven, low-latency alert detection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventListener {

    private final AlertService alertService;

    @KafkaListener(
            topics = "${platform.kafka.topics.job-events:job-monitor.job-events}",
            groupId = "${spring.kafka.consumer.group-id:alerting-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onJobEvent(@Payload JobEvent event, Acknowledgment ack) {
        try {
            log.info("Received job event: action={}, jobId={}, tenant={}",
                    event.getAction(), event.getJobId(), event.getTenantId());

            if (shouldEvaluateAlert(event)) {
                var context = buildEvaluationContext(event);
                var triggered = alertService.evaluateRules(
                        event.getTenantId(),
                        event.getJobId(),
                        context);

                if (!triggered.isEmpty()) {
                    log.info("Job event triggered {} alert(s) for job={}",
                            triggered.size(), event.getJobId());
                }
            }

            Optional.ofNullable(ack).ifPresent(Acknowledgment::acknowledge);
        } catch (Exception e) {
            log.error("Error processing job event for jobId={}: {}",
                    event.getJobId(), e.getMessage(), e);
            // Let Spring Kafka error handler deal with retries
            throw e;
        }
    }

    /**
     * Only evaluate alerts for actionable events — not for REGISTERED or STARTED.
     */
    private boolean shouldEvaluateAlert(JobEvent event) {
        return event.getAction() != null &&
               event.getJobId() != null &&
               event.getTenantId() != null &&
               switch (event.getAction()) {
                   case FAILED, SLA_VIOLATED, HEARTBEAT_MISSED, COMPLETED -> true;
                   case REGISTERED, STARTED, RETRYING -> false;
               };
    }

    /**
     * Builds the evaluation context map from the job event.
     * Keys match what the AlertService condition evaluators expect.
     */
    private Map<String, Object> buildEvaluationContext(JobEvent event) {
        var context = new HashMap<String, Object>();

        context.put("action", event.getAction().name());
        context.put("jobName", Optional.ofNullable(event.getJobName()).orElse("unknown"));

        // Failure context
        if (event.getAction() == JobEvent.Action.FAILED) {
            context.put("failureCount", 1L);
            Optional.ofNullable(event.getExitCode())
                    .ifPresent(code -> context.put("exitCode", code));
            Optional.ofNullable(event.getErrorMessage())
                    .ifPresent(msg -> context.put("errorMessage", msg));
        }

        // SLA/Duration context
        if (event.getExecutionStartTime() != null && event.getExecutionEndTime() != null) {
            long durationMs = java.time.Duration.between(
                    event.getExecutionStartTime(),
                    event.getExecutionEndTime()).toMillis();
            context.put("durationMs", durationMs);
        }

        // Timeout context
        if (event.getAction() == JobEvent.Action.SLA_VIOLATED) {
            context.put("timeoutSeconds", 1L);
        }

        return context;
    }
}
