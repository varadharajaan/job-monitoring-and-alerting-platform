package com.jobmonitor.monitoring.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.platform.common.event.PlatformEvent;
import com.jobmonitor.platform.common.test.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying Kafka producer/consumer with real Kafka container.
 */
@SpringBootTest
@ActiveProfiles("test")
class KafkaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, PlatformEvent> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publishAndConsumeJobEvent() throws Exception {
        String topic = "job-events-test";
        UUID jobId = UUID.randomUUID();

        JobEvent event = JobEvent.builder()
                .tenantId("test-tenant")
                .jobId(jobId)
                .action(JobEvent.Action.COMPLETED)
                .jobName("kafka-integration-test-job")
                .exitCode(0)
                .executionStartTime(Instant.now().minusSeconds(60))
                .executionEndTime(Instant.now())
                .build();

        // Publish
        kafkaTemplate.send(topic, jobId.toString(), event).get();

        // Consume raw to verify
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(topic));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);

            ConsumerRecord<String, String> record = records.iterator().next();
            assertThat(record.key()).isEqualTo(jobId.toString());

            // Verify JSON payload can be deserialized to PlatformEvent
            PlatformEvent deserialized = objectMapper.readValue(record.value(), PlatformEvent.class);
            assertThat(deserialized).isInstanceOf(JobEvent.class);
            JobEvent received = (JobEvent) deserialized;
            assertThat(received.getJobName()).isEqualTo("kafka-integration-test-job");
            assertThat(received.getAction()).isEqualTo(JobEvent.Action.COMPLETED);
        }
    }
}
