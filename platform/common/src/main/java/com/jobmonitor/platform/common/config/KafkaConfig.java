package com.jobmonitor.platform.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.platform.common.event.PlatformEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;

/**
 * Centralized Kafka configuration — topic names, partitions, replicas,
 * error handling — ALL from {@link PlatformProperties.KafkaConfig}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final PlatformProperties platformProps;
    private final KafkaProperties kafkaProperties;

    // ═══════════════ Producer ═══════════════

    @Bean
    public ProducerFactory<String, PlatformEvent> producerFactory(ObjectMapper objectMapper) {
        var props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        var factory = new DefaultKafkaProducerFactory<String, PlatformEvent>(props);
        factory.setValueSerializer(new JsonSerializer<>(objectMapper));
        return factory;
    }

    @Bean
    public KafkaTemplate<String, PlatformEvent> kafkaTemplate(
            ProducerFactory<String, PlatformEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ═══════════════ Consumer Error Handler ═══════════════

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        var errorConfig = platformProps.getKafka().getErrorHandling();
        var backOff = new FixedBackOff(
                errorConfig.getBackoffIntervalMs(),
                errorConfig.getMaxRetries()
        );
        var handler = new DefaultErrorHandler(backOff);
        handler.addNotRetryableExceptions(
                org.apache.kafka.common.errors.SerializationException.class,
                com.fasterxml.jackson.core.JsonParseException.class
        );
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(platformProps.getKafka().getListener().getConcurrency());
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ═══════════════ Topics (auto-create) ═══════════════

    @Bean
    public NewTopic jobEventsTopic() {
        var topics = platformProps.getKafka().getTopics();
        return TopicBuilder.name(topics.getJobEvents())
                .partitions(topics.getDefaultPartitions())
                .replicas(topics.getDefaultReplicas())
                .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        var topics = platformProps.getKafka().getTopics();
        return TopicBuilder.name(topics.getNotificationEvents())
                .partitions(topics.getDefaultPartitions())
                .replicas(topics.getDefaultReplicas())
                .build();
    }

    @Bean
    public NewTopic alertEventsTopic() {
        var topics = platformProps.getKafka().getTopics();
        return TopicBuilder.name(topics.getAlertEvents())
                .partitions(topics.getDefaultPartitions())
                .replicas(topics.getDefaultReplicas())
                .build();
    }

    @Bean
    public NewTopic queueEventsTopic() {
        var topics = platformProps.getKafka().getTopics();
        return TopicBuilder.name(topics.getQueueEvents())
                .partitions(topics.getDefaultPartitions())
                .replicas(topics.getDefaultReplicas())
                .build();
    }

    @Bean
    public NewTopic deadLetterTopic() {
        var topics = platformProps.getKafka().getTopics();
        return TopicBuilder.name(topics.getDeadLetter())
                .partitions(topics.getDefaultPartitions())
                .replicas(topics.getDefaultReplicas())
                .build();
    }
}
