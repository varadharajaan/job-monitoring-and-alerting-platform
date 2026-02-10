package com.jobmonitor.logingest.consumer;

import com.jobmonitor.logingest.model.LogEntry;
import com.jobmonitor.logingest.service.LogAlertPatternService;
import com.jobmonitor.logingest.service.LogSearchService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka consumer that ingests log entries from the {@code log-events} topic.
 * Each batch is bulk-indexed into Elasticsearch and evaluated against
 * the tenant's active alert patterns.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LogKafkaConsumer {

    private final LogSearchService logSearchService;
    private final LogAlertPatternService alertPatternService;

    @KafkaListener(
            topics = "${app.kafka.topics.log-events:log-events}",
            groupId = "log-ingestion-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Timed(value = "log.kafka.consume", description = "Kafka log batch consumption time")
    public void consumeLogs(List<LogEntry> entries, Acknowledgment ack) {
        log.debug("Received {} log entries from Kafka", entries.size());
        try {
            // Bulk-index into Elasticsearch
            int indexed = logSearchService.bulkIndex(entries);

            // Evaluate each entry against alert patterns
            int alertsTriggered = 0;
            for (LogEntry entry : entries) {
                if (alertPatternService.evaluateEntry(entry)) {
                    alertsTriggered++;
                }
            }

            log.info("Processed log batch: indexed={}, alertsTriggered={}", indexed, alertsTriggered);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process log batch: {}", e.getMessage(), e);
            // Don't ack — Kafka will redeliver
        }
    }
}
