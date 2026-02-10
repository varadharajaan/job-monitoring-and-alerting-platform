package com.jobmonitor.platform.common.functional;

import com.jobmonitor.platform.common.event.PlatformEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Default Kafka-backed implementation of {@link EventPublisher}.
 * Uses CompletableFuture callbacks via lambdas for async send confirmation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher<PlatformEvent> {

    private final KafkaTemplate<String, PlatformEvent> kafkaTemplate;

    /** Success callback — logs topic, partition, offset. */
    private static final BiConsumer<SendResult<String, PlatformEvent>, String> ON_SUCCESS =
            (result, topic) -> Optional.ofNullable(result)
                    .map(SendResult::getRecordMetadata)
                    .ifPresent(meta -> log.debug(
                            "Event published to topic={}, partition={}, offset={}",
                            meta.topic(), meta.partition(), meta.offset()));

    /** Failure callback — logs error details. */
    private static final BiConsumer<Throwable, String> ON_FAILURE =
            (ex, topic) -> log.error("Failed to publish event to topic={}: {}",
                    topic, ex.getMessage(), ex);

    @Override
    public void publish(String topic, String key, PlatformEvent event) {
        CompletableFuture<SendResult<String, PlatformEvent>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) ->
                Optional.ofNullable(ex)
                        .ifPresentOrElse(
                                error -> ON_FAILURE.accept(error, topic),
                                ()    -> ON_SUCCESS.accept(result, topic)
                        ));
    }
}
