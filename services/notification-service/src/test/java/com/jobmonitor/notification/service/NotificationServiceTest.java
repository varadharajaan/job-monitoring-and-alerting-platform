package com.jobmonitor.notification.service;

import com.jobmonitor.notification.dto.*;
import com.jobmonitor.notification.entity.Notification;
import com.jobmonitor.notification.entity.NotificationTemplate;
import com.jobmonitor.notification.mapper.NotificationMapper;
import com.jobmonitor.notification.repository.NotificationRepository;
import com.jobmonitor.notification.repository.NotificationTemplateRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.PlatformEvent;
import com.jobmonitor.platform.common.exception.BusinessException;
import com.jobmonitor.platform.common.exception.DuplicateResourceException;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.functional.EventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link NotificationService} — validates functional channel dispatch,
 * template rendering, rate limiting, and Optional chains.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private PlatformProperties properties;
    @Mock private EventPublisher<PlatformEvent> eventPublisher;

    private NotificationService notificationService;

    private static final String TENANT_ID = "tenant-003";
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final String TOPIC = "job-monitor.notification-events";

    // ── Test fixtures ──

    private final Function<UUID, NotificationTemplate> createTemplate = id -> {
        var template = NotificationTemplate.builder()
                .name("alert-email")
                .channel("EMAIL")
                .subject("Alert: {{alertName}}")
                .body("Job {{jobName}} has triggered alert {{alertName}}. Status: {{status}}")
                .variables(List.of("alertName", "jobName", "status"))
                .build();
        template.setId(id);
        template.setTenantId(TENANT_ID);
        template.setCreatedAt(Instant.now());
        template.setVersion(0L);
        return template;
    };

    @BeforeEach
    void setUp() {
        var kafkaConfig = new PlatformProperties.KafkaConfig();
        var topics = new PlatformProperties.KafkaConfig.Topics();
        topics.setNotificationEvents(TOPIC);
        kafkaConfig.setTopics(topics);
        lenient().when(properties.getKafka()).thenReturn(kafkaConfig);

        var notifConfig = new PlatformProperties.NotificationConfig();
        var rateLimit = new PlatformProperties.NotificationConfig.RateLimit();
        rateLimit.setEmail(100);
        rateLimit.setSms(50);
        rateLimit.setSlack(200);
        rateLimit.setPush(300);
        notifConfig.setRateLimit(rateLimit);
        lenient().when(properties.getNotification()).thenReturn(notifConfig);

        notificationService = new NotificationService(
                notificationRepository, templateRepository, notificationMapper,
                properties, eventPublisher,
                java.util.Map.of(
                    "EMAIL", (recipient, subject, body, metadata) -> "email-test-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                    "SMS", (recipient, subject, body, metadata) -> "sms-test-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                    "SLACK", (recipient, subject, body, metadata) -> "slack-test-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                    "PUSH", (recipient, subject, body, metadata) -> "push-test-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                    "WEBHOOK", (recipient, subject, body, metadata) -> "webhook-test-" + java.util.UUID.randomUUID().toString().substring(0, 8)
                ));
    }

    // ──────────── Template Tests ────────────

    @Nested
    @DisplayName("createTemplate()")
    class CreateTemplateTests {

        @Test
        @DisplayName("should create template successfully")
        void shouldCreateTemplate() {
            var request = TemplateRequest.builder()
                    .name("alert-email")
                    .channel("EMAIL")
                    .subject("Alert: {{alertName}}")
                    .body("Body content")
                    .build();

            var entity = createTemplate.apply(TEMPLATE_ID);
            var response = TemplateResponse.builder()
                    .id(TEMPLATE_ID)
                    .name("alert-email")
                    .channel("EMAIL")
                    .build();

            given(templateRepository.findByTenantIdAndNameAndChannel(TENANT_ID, "alert-email", "EMAIL"))
                    .willReturn(Optional.empty());
            given(notificationMapper.toTemplateEntity(request)).willReturn(entity);
            given(templateRepository.save(any())).willReturn(entity);
            given(notificationMapper.toTemplateResponse(entity)).willReturn(response);

            var result = notificationService.createTemplate(TENANT_ID, request);

            assertThat(result)
                    .isNotNull()
                    .satisfies(r -> assertThat(r.getName()).isEqualTo("alert-email"));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException on duplicate template")
        void shouldThrowOnDuplicate() {
            var request = TemplateRequest.builder()
                    .name("alert-email")
                    .channel("EMAIL")
                    .body("Body")
                    .build();

            given(templateRepository.findByTenantIdAndNameAndChannel(TENANT_ID, "alert-email", "EMAIL"))
                    .willReturn(Optional.of(createTemplate.apply(UUID.randomUUID())));

            assertThatThrownBy(() -> notificationService.createTemplate(TENANT_ID, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    // ──────────── Send Notification Tests ────────────

    @Nested
    @DisplayName("sendNotification()")
    class SendNotificationTests {

        @Test
        @DisplayName("should send notification with template rendering")
        void shouldSendWithTemplate() {
            var template = createTemplate.apply(TEMPLATE_ID);
            var request = SendNotificationRequest.builder()
                    .templateId(TEMPLATE_ID)
                    .channel("EMAIL")
                    .recipient("user@example.com")
                    .templateVariables(Map.of(
                            "alertName", "high-failure-rate",
                            "jobName", "etl-pipeline",
                            "status", "CRITICAL"))
                    .build();

            var notification = Notification.builder()
                    .tenantId(TENANT_ID)
                    .channel("EMAIL")
                    .recipient("user@example.com")
                    .status("PENDING")
                    .build();
            notification.setId(UUID.randomUUID());

            var response = NotificationResponse.builder()
                    .id(notification.getId())
                    .channel("EMAIL")
                    .status("SENT")
                    .build();

            given(notificationRepository.countByTenantIdAndChannelSince(eq(TENANT_ID), eq("EMAIL"), any()))
                    .willReturn(5L);
            given(templateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                    .willReturn(Optional.of(template));
            given(notificationRepository.save(any(Notification.class))).willReturn(notification);
            given(notificationMapper.toNotificationResponse(any())).willReturn(response);

            var result = notificationService.sendNotification(TENANT_ID, request);

            assertThat(result).isNotNull();
            then(eventPublisher).should().publish(eq(TOPIC), eq(TENANT_ID), any());
        }

        @Test
        @DisplayName("should send notification with direct body (no template)")
        void shouldSendWithDirectBody() {
            var request = SendNotificationRequest.builder()
                    .channel("SLACK")
                    .recipient("#alerts-channel")
                    .subject("Alert!")
                    .body("Direct notification body")
                    .build();

            var notification = Notification.builder()
                    .tenantId(TENANT_ID)
                    .channel("SLACK")
                    .recipient("#alerts-channel")
                    .status("PENDING")
                    .build();
            notification.setId(UUID.randomUUID());

            var response = NotificationResponse.builder()
                    .id(notification.getId())
                    .channel("SLACK")
                    .status("SENT")
                    .build();

            given(notificationRepository.countByTenantIdAndChannelSince(eq(TENANT_ID), eq("SLACK"), any()))
                    .willReturn(0L);
            given(notificationRepository.save(any())).willReturn(notification);
            given(notificationMapper.toNotificationResponse(any())).willReturn(response);

            var result = notificationService.sendNotification(TENANT_ID, request);

            assertThat(result.getChannel()).isEqualTo("SLACK");
        }

        @Test
        @DisplayName("should throw BusinessException when rate limit exceeded")
        void shouldThrowOnRateLimit() {
            var request = SendNotificationRequest.builder()
                    .channel("EMAIL")
                    .recipient("user@example.com")
                    .body("Body")
                    .build();

            given(notificationRepository.countByTenantIdAndChannelSince(eq(TENANT_ID), eq("EMAIL"), any()))
                    .willReturn(150L); // exceeds limit of 100

            assertThatThrownBy(() -> notificationService.sendNotification(TENANT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).contains("Rate limit exceeded"));
        }
    }

    // ──────────── getTemplate Tests ────────────

    @Nested
    @DisplayName("getTemplate()")
    class GetTemplateTests {

        @Test
        @DisplayName("should return template when found")
        void shouldReturnTemplate() {
            var entity = createTemplate.apply(TEMPLATE_ID);
            var response = TemplateResponse.builder().id(TEMPLATE_ID).name("alert-email").build();

            given(templateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                    .willReturn(Optional.of(entity));
            given(notificationMapper.toTemplateResponse(entity)).willReturn(response);

            var result = notificationService.getTemplate(TENANT_ID, TEMPLATE_ID);

            assertThat(result.getId()).isEqualTo(TEMPLATE_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when template not found")
        void shouldThrowWhenNotFound() {
            given(templateRepository.findByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.getTemplate(TENANT_ID, TEMPLATE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ──────────── listTemplates Tests ────────────

    @Nested
    @DisplayName("listTemplates()")
    class ListTemplatesTests {

        @Test
        @DisplayName("should filter by channel when provided via Optional")
        void shouldFilterByChannel() {
            var pageable = PageRequest.of(0, 20);
            var page = new PageImpl<>(List.of(createTemplate.apply(TEMPLATE_ID)), pageable, 1);
            var response = TemplateResponse.builder().id(TEMPLATE_ID).channel("EMAIL").build();

            given(templateRepository.findByTenantIdAndChannel(TENANT_ID, "EMAIL", pageable))
                    .willReturn(page);
            given(notificationMapper.toTemplateResponse(any())).willReturn(response);

            var result = notificationService.listTemplates(TENANT_ID, Optional.of("EMAIL"), pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }
}
