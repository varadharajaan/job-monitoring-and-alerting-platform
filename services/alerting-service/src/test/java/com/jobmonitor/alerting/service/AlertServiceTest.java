package com.jobmonitor.alerting.service;

import com.jobmonitor.alerting.dto.*;
import com.jobmonitor.alerting.entity.AlertHistory;
import com.jobmonitor.alerting.entity.AlertRule;
import com.jobmonitor.alerting.mapper.AlertMapper;
import com.jobmonitor.alerting.repository.AlertHistoryRepository;
import com.jobmonitor.alerting.repository.AlertRuleRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.PlatformEvent;
import com.jobmonitor.platform.common.exception.DuplicateResourceException;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.functional.EventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link AlertService} — validates functional dispatch, Optional chains,
 * EntityValidator, cooldown check, and condition evaluation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService Unit Tests")
class AlertServiceTest {

    @Mock private AlertRuleRepository ruleRepository;
    @Mock private AlertHistoryRepository historyRepository;
    @Mock private AlertMapper alertMapper;
    @Mock private PlatformProperties properties;
    @Mock private EventPublisher<PlatformEvent> eventPublisher;

    private AlertService alertService;

    private static final String TENANT_ID = "tenant-002";
    private static final UUID RULE_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final String TOPIC = "job-monitor.alert-events";

    // ── Test fixtures ──

    private final Function<UUID, AlertRule> createRule = id -> {
        var rule = AlertRule.builder()
                .name("high-failure-rate")
                .description("Fires when failure count exceeds threshold")
                .jobId(JOB_ID)
                .ruleType("FAILURE_COUNT")
                .conditionJson("{\"threshold\": 5}")
                .severity("HIGH")
                .notificationChannels(List.of("EMAIL", "SLACK"))
                .cooldownSeconds(300)
                .enabled(true)
                .build();
        rule.setId(id);
        rule.setTenantId(TENANT_ID);
        rule.setCreatedAt(Instant.now());
        rule.setVersion(0L);
        return rule;
    };

    private final Function<UUID, AlertRuleResponse> createRuleResponse = id ->
            AlertRuleResponse.builder()
                    .id(id)
                    .tenantId(TENANT_ID)
                    .name("high-failure-rate")
                    .ruleType("FAILURE_COUNT")
                    .severity("HIGH")
                    .enabled(true)
                    .build();

    @BeforeEach
    void setUp() {
        var kafkaConfig = new PlatformProperties.KafkaConfig();
        var topics = new PlatformProperties.KafkaConfig.Topics();
        topics.setAlertEvents(TOPIC);
        kafkaConfig.setTopics(topics);
        lenient().when(properties.getKafka()).thenReturn(kafkaConfig);

        // Construct alertService via constructor (it initializes functional fields)
        alertService = new AlertService(ruleRepository, historyRepository, alertMapper,
                properties, eventPublisher);
    }

    // ──────────── createRule Tests ────────────

    @Nested
    @DisplayName("createRule()")
    class CreateRuleTests {

        @Test
        @DisplayName("should create alert rule and publish event")
        void shouldCreateRule() {
            var request = AlertRuleRequest.builder()
                    .name("high-failure-rate")
                    .ruleType("FAILURE_COUNT")
                    .conditionJson("{\"threshold\": 5}")
                    .severity("HIGH")
                    .notificationChannels(List.of("EMAIL"))
                    .build();

            var entity = createRule.apply(RULE_ID);
            var response = createRuleResponse.apply(RULE_ID);

            given(alertMapper.toRuleEntity(request)).willReturn(entity);
            given(ruleRepository.findByTenantIdAndName(TENANT_ID, "high-failure-rate"))
                    .willReturn(Optional.empty());
            given(ruleRepository.save(any(AlertRule.class))).willReturn(entity);
            given(alertMapper.toRuleResponse(entity)).willReturn(response);

            var result = alertService.createRule(TENANT_ID, request);

            assertThat(result)
                    .isNotNull()
                    .satisfies(r -> {
                        assertThat(r.getId()).isEqualTo(RULE_ID);
                        assertThat(r.getRuleType()).isEqualTo("FAILURE_COUNT");
                    });

            then(ruleRepository).should().save(any(AlertRule.class));
            then(eventPublisher).should().publish(eq(TOPIC), eq(TENANT_ID), any());
        }

        @Test
        @DisplayName("should throw DuplicateResourceException on duplicate rule name")
        void shouldThrowOnDuplicate() {
            var request = AlertRuleRequest.builder()
                    .name("high-failure-rate")
                    .ruleType("FAILURE_COUNT")
                    .conditionJson("{\"threshold\": 5}")
                    .build();

            var existing = createRule.apply(UUID.randomUUID());
            var newRule = createRule.apply(UUID.randomUUID());

            given(alertMapper.toRuleEntity(request)).willReturn(newRule);
            given(ruleRepository.findByTenantIdAndName(TENANT_ID, "high-failure-rate"))
                    .willReturn(Optional.of(existing));

            assertThatThrownBy(() -> alertService.createRule(TENANT_ID, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    // ──────────── getRule Tests ────────────

    @Nested
    @DisplayName("getRule()")
    class GetRuleTests {

        @Test
        @DisplayName("should return rule response when found")
        void shouldReturnRule() {
            var entity = createRule.apply(RULE_ID);
            var response = createRuleResponse.apply(RULE_ID);

            given(ruleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID)).willReturn(Optional.of(entity));
            given(alertMapper.toRuleResponse(entity)).willReturn(response);

            var result = alertService.getRule(TENANT_ID, RULE_ID);

            assertThat(result.getId()).isEqualTo(RULE_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when rule not found")
        void shouldThrowWhenNotFound() {
            given(ruleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> alertService.getRule(TENANT_ID, RULE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ──────────── evaluateRules Tests (functional dispatch) ────────────

    @Nested
    @DisplayName("evaluateRules()")
    class EvaluateRulesTests {

        @Test
        @DisplayName("should trigger alert when FAILURE_COUNT condition is met")
        void shouldTriggerOnFailureCount() {
            var rule = createRule.apply(RULE_ID);
            var context = Map.<String, Object>of("failureCount", 10);

            given(ruleRepository.findActiveRulesForJob(TENANT_ID, JOB_ID)).willReturn(List.of(rule));
            given(historyRepository.findFirstByAlertRuleIdOrderByTriggeredAtDesc(RULE_ID))
                    .willReturn(Optional.empty());
            given(historyRepository.save(any(AlertHistory.class)))
                    .willAnswer(inv -> {
                        var history = inv.getArgument(0, AlertHistory.class);
                        history.setId(UUID.randomUUID());
                        return history;
                    });
            given(alertMapper.toHistoryResponse(any()))
                    .willReturn(AlertHistoryResponse.builder()
                            .alertRuleId(RULE_ID)
                            .severity("HIGH")
                            .status("TRIGGERED")
                            .build());

            var result = alertService.evaluateRules(TENANT_ID, JOB_ID, context);

            assertThat(result)
                    .hasSize(1)
                    .first()
                    .satisfies(h -> {
                        assertThat(h.getSeverity()).isEqualTo("HIGH");
                        assertThat(h.getStatus()).isEqualTo("TRIGGERED");
                    });
        }

        @Test
        @DisplayName("should not trigger when failure count is below threshold")
        void shouldNotTriggerBelowThreshold() {
            var rule = createRule.apply(RULE_ID);
            var context = Map.<String, Object>of("failureCount", 2);

            given(ruleRepository.findActiveRulesForJob(TENANT_ID, JOB_ID)).willReturn(List.of(rule));
            given(historyRepository.findFirstByAlertRuleIdOrderByTriggeredAtDesc(RULE_ID))
                    .willReturn(Optional.empty());

            var result = alertService.evaluateRules(TENANT_ID, JOB_ID, context);

            assertThat(result).isEmpty();
            then(historyRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("should respect cooldown period and skip rule still in cooldown")
        void shouldRespectCooldown() {
            var rule = createRule.apply(RULE_ID);
            rule.setCooldownSeconds(3600); // 1 hour cooldown

            var recentHistory = AlertHistory.builder()
                    .alertRule(rule)
                    .triggeredAt(Instant.now().minus(30, ChronoUnit.MINUTES)) // triggered 30 min ago
                    .build();

            var context = Map.<String, Object>of("failureCount", 10);

            given(ruleRepository.findActiveRulesForJob(TENANT_ID, JOB_ID)).willReturn(List.of(rule));
            given(historyRepository.findFirstByAlertRuleIdOrderByTriggeredAtDesc(RULE_ID))
                    .willReturn(Optional.of(recentHistory));

            var result = alertService.evaluateRules(TENANT_ID, JOB_ID, context);

            assertThat(result).isEmpty();
        }
    }

    // ──────────── toggleRule Tests ────────────

    @Nested
    @DisplayName("toggleRule()")
    class ToggleRuleTests {

        @Test
        @DisplayName("should toggle rule enabled state")
        void shouldToggle() {
            var rule = createRule.apply(RULE_ID);
            given(ruleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID)).willReturn(Optional.of(rule));
            given(ruleRepository.save(any())).willReturn(rule);

            alertService.toggleRule(TENANT_ID, RULE_ID, false);

            then(ruleRepository).should().save(argThat(r -> !r.getEnabled()));
        }
    }
}
