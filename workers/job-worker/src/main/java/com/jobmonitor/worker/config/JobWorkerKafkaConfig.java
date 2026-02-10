package com.jobmonitor.worker.config;

import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.JobEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * Kafka consumer configuration for job-worker.
 * Error handler with configurable backoff and max retries from platform config.
 */
@Configuration
@RequiredArgsConstructor
public class JobWorkerKafkaConfig {

    private final KafkaProperties kafkaProperties;
    private final PlatformProperties platformProperties;

    @Bean
    public ConsumerFactory<String, JobEvent> consumerFactory() {
        var props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.jobmonitor.*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, JobEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, JobEvent> kafkaListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, JobEvent>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(platformProperties.getKafka().getListener().getConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // Error handler with configurable retries
        var errorHandling = platformProperties.getKafka().getErrorHandling();
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(errorHandling.getBackoffIntervalMs(), errorHandling.getMaxRetries())
        ));

        return factory;
    }
}
