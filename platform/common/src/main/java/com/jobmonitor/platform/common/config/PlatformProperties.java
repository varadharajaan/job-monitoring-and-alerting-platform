package com.jobmonitor.platform.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Centralized configuration properties for the entire platform.
 * <p>
 * ZERO hardcoded values — every tunable parameter is externalized here
 * and bound from {@code application.yml} under the {@code platform.*} prefix.
 * <p>
 * Override per environment via {@code application-{profile}.yml} or env vars.
 *
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config">Spring External Config</a>
 */
@ConfigurationProperties(prefix = "platform")
@Getter
@Setter
public class PlatformProperties {

    private final Async async = new Async();
    private final KafkaConfig kafka = new KafkaConfig();
    private final CacheConfig cache = new CacheConfig();
    private final S3Config s3 = new S3Config();
    private final NotificationConfig notification = new NotificationConfig();
    private final JobMonitorConfig jobMonitor = new JobMonitorConfig();
    private final JobQueueConfig jobQueue = new JobQueueConfig();
    private final SecurityConfig security = new SecurityConfig();
    private final LoggingProperties logging = new LoggingProperties();

    // ═══════════════ Async Thread Pools ═══════════════

    @Getter @Setter
    public static class Async {
        private Pool taskPool = new Pool(4, 16, 100, "async-", 30);
        private Pool notificationPool = new Pool(2, 8, 200, "notif-", 60);
        private Pool jobQueuePool = new Pool(4, 12, 50, "job-queue-", 120);

        @Getter @Setter
        public static class Pool {
            private int coreSize;
            private int maxSize;
            private int queueCapacity;
            private String threadPrefix;
            private int awaitTerminationSeconds;

            public Pool() {}

            public Pool(int coreSize, int maxSize, int queueCapacity,
                        String threadPrefix, int awaitTerminationSeconds) {
                this.coreSize = coreSize;
                this.maxSize = maxSize;
                this.queueCapacity = queueCapacity;
                this.threadPrefix = threadPrefix;
                this.awaitTerminationSeconds = awaitTerminationSeconds;
            }
        }
    }

    // ═══════════════ Kafka ═══════════════

    @Getter @Setter
    public static class KafkaConfig {
        private Topics topics = new Topics();
        private Listener listener = new Listener();
        private ErrorHandling errorHandling = new ErrorHandling();

        @Getter @Setter
        public static class Topics {
            private String jobEvents = "job-monitor.job-events";
            private String notificationEvents = "job-monitor.notification-events";
            private String queryEvents = "job-monitor.query-events";
            private String queueEvents = "job-monitor.queue-events";
            private String alertEvents = "job-monitor.alert-events";
            private String deadLetter = "job-monitor.dead-letter";
            private int defaultPartitions = 6;
            private short defaultReplicas = 1;
        }

        @Getter @Setter
        public static class Listener {
            private int concurrency = 3;
        }

        @Getter @Setter
        public static class ErrorHandling {
            private long backoffIntervalMs = 1000L;
            private long maxRetries = 3;
        }
    }

    // ═══════════════ Cache (Redis) ═══════════════

    @Getter @Setter
    public static class CacheConfig {
        private Duration defaultTtl = Duration.ofMinutes(15);
        private Map<String, Duration> ttls = Map.of(
            "jobs",          Duration.ofMinutes(5),
            "notifications", Duration.ofMinutes(10),
            "templates",     Duration.ofHours(1),
            "rate-limits",   Duration.ofMinutes(1),
            "api-keys",      Duration.ofMinutes(30)
        );
    }

    // ═══════════════ S3 (Log Upload) ═══════════════

    @Getter @Setter
    public static class S3Config {
        private boolean enabled;
        private String bucket = "job-monitor-logs";
        private String region = "us-east-1";
        private String endpoint;
        private String logUploadCron = "0 0 * * * *";
        private String pathPrefix = "logs";
    }

    // ═══════════════ Notification ═══════════════

    @Getter @Setter
    public static class NotificationConfig {
        private RateLimit rateLimit = new RateLimit();

        @Getter @Setter
        public static class RateLimit {
            private int email = 100;
            private int sms = 50;
            private int slack = 200;
            private int push = 300;
        }
    }

    // ═══════════════ Job Monitor ═══════════════

    @Getter @Setter
    public static class JobMonitorConfig {
        private long healthCheckIntervalMs = 60_000L;
        private String slaEvaluationCron = "0 */5 * * * *";
        private int maxRetryAttempts = 3;
        private double retryBackoffMultiplier = 2.0;
        private Duration executionRetentionPeriod = Duration.ofDays(90);
    }

    // ═══════════════ Job Queue ═══════════════

    @Getter @Setter
    public static class JobQueueConfig {
        private int workerCount = 4;
        private long pollIntervalMs = 5_000L;
        private Duration maxExecutionTime = Duration.ofHours(1);
        private int maxRetries = 3;
        private Duration lockTimeout = Duration.ofMinutes(5);
    }

    // ═══════════════ Security ═══════════════

    @Getter @Setter
    public static class SecurityConfig {
        private String[] publicPaths = {
            "/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health/**",
            "/actuator/info"
        };
        private String[] adminPaths = {
            "/actuator/**"
        };
    }
}
