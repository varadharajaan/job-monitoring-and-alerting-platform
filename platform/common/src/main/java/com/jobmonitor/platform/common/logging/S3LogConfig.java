package com.jobmonitor.platform.common.logging;

import com.jobmonitor.platform.common.config.PlatformProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Auto-configures S3Client only when S3 log upload is enabled.
 * <p>
 * For local dev, endpoint is overridden to LocalStack.
 * For prod, uses IAM Instance Profile / IRSA credentials.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "platform.s3", name = "enabled", havingValue = "true")
public class S3LogConfig {

    @Bean
    public S3Client s3Client(PlatformProperties properties) {
        var s3Config = properties.getS3();
        var builder = S3Client.builder()
                .region(Region.of(s3Config.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create());

        // LocalStack or custom endpoint override
        if (s3Config.getEndpoint() != null && !s3Config.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3Config.getEndpoint()))
                    .forcePathStyle(true);
            log.info("S3 client configured with custom endpoint: {}", s3Config.getEndpoint());
        }

        return builder.build();
    }
}
