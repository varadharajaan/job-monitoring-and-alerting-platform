package com.jobmonitor.logingest.config;

import com.jobmonitor.logingest.model.LogEntry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * Kafka consumer configuration for batch log ingestion.
 */
@Configuration
public class LogKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, LogEntry> logConsumerFactory() {
        JsonDeserializer<LogEntry> jsonDeserializer = new JsonDeserializer<>(LogEntry.class);
        jsonDeserializer.addTrustedPackages("com.jobmonitor.*");
        jsonDeserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG, "log-ingestion-group",
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500,
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
                ),
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(jsonDeserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LogEntry> kafkaListenerContainerFactory(
            ConsumerFactory<String, LogEntry> logConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, LogEntry> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(logConsumerFactory);
        factory.setConcurrency(3);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
