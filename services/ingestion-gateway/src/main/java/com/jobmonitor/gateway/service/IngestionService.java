package com.jobmonitor.gateway.service;

import com.jobmonitor.gateway.dto.IngestionRequest;
import com.jobmonitor.gateway.dto.IngestionResponse;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.platform.common.functional.EventPublisher;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * Ingestion service — validates, transforms, and publishes job events to Kafka.
 * <p>
 * Uses functional patterns: Function for event mapping, Supplier for topic resolution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final EventPublisher eventPublisher;
    private final PlatformProperties platformProperties;

    /** Map ingestion request -> domain event */
    private final Function<IngestionContext, JobEvent> toJobEvent = ctx ->
            JobEvent.builder()
                    .tenantId(ctx.tenantId())
                    .jobName(ctx.request().getJobName())
                    .action(resolveAction(ctx.request().getEventType()))
                    .cronExpression(ctx.request().getCronExpression())
                    .exitCode(ctx.request().getExitCode())
                    .errorMessage(ctx.request().getErrorMessage())
                    .metadata(ctx.request().getMetadata())
                    .executionStartTime(Instant.now())
                    .build();

    @Timed(value = "gateway.ingest", description = "Time to ingest and publish a job event")
    public IngestionResponse ingest(String tenantId, IngestionRequest request) {
        var context = new IngestionContext(tenantId, request);
        JobEvent event = toJobEvent.apply(context);

        String topic = getJobEventsTopic();
        String key = tenantId + ":" + request.getJobName();
        eventPublisher.publish(topic, key, event);

        log.info("Ingested event: tenant={}, job={}, type={}", tenantId, request.getJobName(), request.getEventType());

        return IngestionResponse.builder()
                .eventId(event.getEventId())
                .status("ACCEPTED")
                .receivedAt(Instant.now())
                .build();
    }

    private JobEvent.Action resolveAction(String eventType) {
        return Optional.ofNullable(eventType)
                .map(String::toUpperCase)
                .map(type -> switch (type) {
                    case "STARTED" -> JobEvent.Action.STARTED;
                    case "COMPLETED" -> JobEvent.Action.COMPLETED;
                    case "FAILED" -> JobEvent.Action.FAILED;
                    case "HEARTBEAT" -> JobEvent.Action.HEARTBEAT_MISSED;
                    case "SLA_VIOLATED" -> JobEvent.Action.SLA_VIOLATED;
                    case "RETRYING" -> JobEvent.Action.RETRYING;
                    default -> JobEvent.Action.REGISTERED;
                })
                .orElse(JobEvent.Action.REGISTERED);
    }

    /** Immutable context record */
    private record IngestionContext(String tenantId, IngestionRequest request) {}

    private String getJobEventsTopic() {
        return Optional.ofNullable(platformProperties.getKafka())
                .map(PlatformProperties.KafkaConfig::getTopics)
                .map(PlatformProperties.KafkaConfig.Topics::getJobEvents)
                .orElse("job-monitor.job-events");
    }
}
