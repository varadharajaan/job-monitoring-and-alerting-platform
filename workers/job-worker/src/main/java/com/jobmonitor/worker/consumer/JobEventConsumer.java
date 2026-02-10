package com.jobmonitor.worker.consumer;

import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.worker.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Kafka consumer for job events — processes events from job-monitor.job-events topic.
 * <p>
 * Uses manual acknowledgment for at-least-once delivery guarantee.
 * Functional Consumer pattern for action dispatch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobEventConsumer {

    private final JobExecutionService jobExecutionService;

    @KafkaListener(
            topics = "${platform.kafka.topics.job-events:job-monitor.job-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload JobEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received job event: action={}, jobName={}, tenant={}, partition={}, offset={}",
                event.getAction(), event.getJobName(), event.getTenantId(), partition, offset);

        try {
            Optional.ofNullable(event.getAction())
                    .map(this::resolveHandler)
                    .ifPresentOrElse(
                            handler -> handler.accept(event),
                            () -> log.warn("Unknown action in event: {}", event.getAction())
                    );
        } catch (Exception ex) {
            log.error("Failed to process job event: action={}, jobName={}, error={}",
                    event.getAction(), event.getJobName(), ex.getMessage(), ex);
            // Let error handler / DLT take care of retries
            throw ex;
        }
    }

    /**
     * Resolve handler for each action type — functional dispatch.
     */
    private Consumer<JobEvent> resolveHandler(JobEvent.Action action) {
        return switch (action) {
            case REGISTERED -> jobExecutionService::handleJobRegistered;
            case STARTED -> jobExecutionService::handleJobStarted;
            case COMPLETED -> jobExecutionService::handleJobCompleted;
            case FAILED -> jobExecutionService::handleJobFailed;
            case SLA_VIOLATED -> jobExecutionService::handleSlaViolation;
            case RETRYING -> jobExecutionService::handleJobRetry;
            case HEARTBEAT_MISSED -> jobExecutionService::handleHeartbeatMissed;
        };
    }
}
