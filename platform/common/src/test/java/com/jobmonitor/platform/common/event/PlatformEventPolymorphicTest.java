package com.jobmonitor.platform.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformEventPolymorphicTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void jobEvent_deserializesViaPolymorphicType() throws Exception {
        String json = """
                {
                  "eventId": "e1",
                  "eventType": "JOB",
                  "source": "job-monitoring-service",
                  "tenantId": "t1",
                  "jobName": "test-job",
                  "action": "STARTED"
                }
                """;
        PlatformEvent event = objectMapper.readValue(json, PlatformEvent.class);
        assertThat(event).isInstanceOf(JobEvent.class);
        assertThat(((JobEvent) event).getJobName()).isEqualTo("test-job");
    }

    @Test
    void alertEvent_deserializesViaPolymorphicType() throws Exception {
        String json = """
                {
                  "eventId": "e2",
                  "eventType": "ALERT",
                  "source": "alerting-service",
                  "tenantId": "t1",
                  "alertName": "high-cpu",
                  "action": "TRIGGERED",
                  "severity": "HIGH"
                }
                """;
        PlatformEvent event = objectMapper.readValue(json, PlatformEvent.class);
        assertThat(event).isInstanceOf(AlertEvent.class);
        assertThat(((AlertEvent) event).getAlertName()).isEqualTo("high-cpu");
    }

    @Test
    void notificationEvent_deserializesViaPolymorphicType() throws Exception {
        String json = """
                {
                  "eventId": "e3",
                  "eventType": "NOTIFICATION",
                  "source": "notification-service",
                  "tenantId": "t1",
                  "action": "SENT",
                  "channel": "EMAIL",
                  "recipient": "ops@test.com"
                }
                """;
        PlatformEvent event = objectMapper.readValue(json, PlatformEvent.class);
        assertThat(event).isInstanceOf(NotificationEvent.class);
        assertThat(((NotificationEvent) event).getRecipient()).isEqualTo("ops@test.com");
    }

    @Test
    void queueEvent_deserializesViaPolymorphicType() throws Exception {
        String json = """
                {
                  "eventId": "e4",
                  "eventType": "QUEUE",
                  "source": "job-queue-service",
                  "tenantId": "t1",
                  "action": "ENQUEUED",
                  "jobType": "EXPORT",
                  "priority": 5
                }
                """;
        PlatformEvent event = objectMapper.readValue(json, PlatformEvent.class);
        assertThat(event).isInstanceOf(QueueEvent.class);
        assertThat(((QueueEvent) event).getJobType()).isEqualTo("EXPORT");
        assertThat(((QueueEvent) event).getPriority()).isEqualTo(5);
    }
}
