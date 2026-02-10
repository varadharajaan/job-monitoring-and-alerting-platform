package com.jobmonitor.alerting.service;

import com.jobmonitor.alerting.dto.*;
import com.jobmonitor.alerting.entity.AlertHistory;
import com.jobmonitor.alerting.entity.AlertRule;
import com.jobmonitor.alerting.mapper.AlertMapper;
import com.jobmonitor.alerting.repository.AlertHistoryRepository;
import com.jobmonitor.alerting.repository.AlertRuleRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.dto.PageResponse;
import com.jobmonitor.platform.common.event.AlertEvent;
import com.jobmonitor.platform.common.event.PlatformEvent;
import com.jobmonitor.platform.common.exception.DuplicateResourceException;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.functional.AlertConditionEvaluator;
import com.jobmonitor.platform.common.functional.EntityValidator;
import com.jobmonitor.platform.common.functional.EventPublisher;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * Alert service with functional dispatch registry, EntityValidator, Optional chains.
 * <p>
 * The alert condition evaluator registry uses a {@code Map<String, AlertConditionEvaluator>}
 * to dispatch evaluation to the correct handler by rule type — no if/else or switch.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AlertService {

    private final AlertRuleRepository ruleRepository;
    private final AlertHistoryRepository historyRepository;
    private final AlertMapper alertMapper;
    private final PlatformProperties properties;
    private final EventPublisher<PlatformEvent> eventPublisher;

    // ──────────── Functional Dispatch Registry ────────────

    /** Evaluates conditions by rule type — extensible dispatch map. */
    private final Map<String, AlertConditionEvaluator> conditionEvaluators;

    /** Validates rule name uniqueness within a tenant. */
    private final EntityValidator<AlertRule> uniqueNameValidator;

    /** Transforms an AlertRule entity to response DTO. */
    private final Function<AlertRule, AlertRuleResponse> toRuleResponse;

    /** Transforms an AlertHistory entity to response DTO. */
    private final Function<AlertHistory, AlertHistoryResponse> toHistoryResponse;

    /** Resolves Kafka topic name from config — zero hardcoded strings. */
    private final Supplier<String> alertEventsTopic;

    public AlertService(AlertRuleRepository ruleRepository,
                        AlertHistoryRepository historyRepository,
                        AlertMapper alertMapper,
                        PlatformProperties properties,
                        EventPublisher<PlatformEvent> eventPublisher) {
        this.ruleRepository = ruleRepository;
        this.historyRepository = historyRepository;
        this.alertMapper = alertMapper;
        this.properties = properties;
        this.eventPublisher = eventPublisher;

        // Functional references
        this.toRuleResponse = alertMapper::toRuleResponse;
        this.toHistoryResponse = alertMapper::toHistoryResponse;
        this.alertEventsTopic = () -> properties.getKafka().getTopics().getAlertEvents();

        // EntityValidator — duplicate check via Optional
        this.uniqueNameValidator = rule ->
                ruleRepository.findByTenantIdAndName(rule.getTenantId(), rule.getName())
                        .filter(existing -> !existing.getId().equals(rule.getId()))
                        .ifPresent(existing -> {
                            throw new DuplicateResourceException("AlertRule", rule.getName());
                        });

        // Condition evaluator registry — each entry is a lambda
        this.conditionEvaluators = Map.of(
                "FAILURE_COUNT", (conditionJson, context) ->
                        extractThreshold(conditionJson)
                                .map(threshold -> getContextValue(context, "failureCount")
                                        .filter(count -> count >= threshold)
                                        .isPresent())
                                .orElse(false),

                "SLA_BREACH", (conditionJson, context) ->
                        extractThreshold(conditionJson)
                                .map(threshold -> getContextValue(context, "durationMs")
                                        .filter(duration -> duration > threshold)
                                        .isPresent())
                                .orElse(false),

                "CONSECUTIVE_FAILURES", (conditionJson, context) ->
                        extractThreshold(conditionJson)
                                .map(threshold -> getContextValue(context, "consecutiveFailures")
                                        .filter(count -> count >= threshold)
                                        .isPresent())
                                .orElse(false),

                "SUCCESS_RATE_DROP", (conditionJson, context) ->
                        extractThreshold(conditionJson)
                                .map(threshold -> getContextValue(context, "successRate")
                                        .filter(rate -> rate < threshold)
                                        .isPresent())
                                .orElse(false),

                "TIMEOUT", (conditionJson, context) ->
                        extractThreshold(conditionJson)
                                .map(threshold -> getContextValue(context, "timeoutSeconds")
                                        .filter(timeout -> timeout >= threshold)
                                        .isPresent())
                                .orElse(false)
        );
    }

    // ──────────── Rule CRUD ────────────

    @Transactional
    @Timed(value = "alert.rule.create", description = "Time to create an alert rule")
    public AlertRuleResponse createRule(String tenantId, AlertRuleRequest request) {
        log.info("Creating alert rule '{}' for tenant={}", request.getName(), tenantId);

        var rule = alertMapper.toRuleEntity(request);
        rule.setTenantId(tenantId);

        Optional.ofNullable(request.getSeverity())
                .filter(s -> !s.isBlank())
                .ifPresent(rule::setSeverity);

        Optional.ofNullable(request.getNotificationChannels())
                .filter(channels -> !channels.isEmpty())
                .ifPresent(rule::setNotificationChannels);

        uniqueNameValidator.validate(rule);

        var saved = ruleRepository.save(rule);
        publishAlertEvent(saved, AlertEvent.Action.TRIGGERED, builder -> {});

        return toRuleResponse.apply(saved);
    }

    public AlertRuleResponse getRule(String tenantId, UUID ruleId) {
        return findRuleOrThrow(tenantId, ruleId)
                .map(toRuleResponse)
                .orElseThrow(notFound("AlertRule", ruleId));
    }

    public PageResponse<AlertRuleResponse> listRules(String tenantId, Optional<Boolean> enabled,
                                                      Pageable pageable) {
        var page = enabled
                .map(e -> ruleRepository.findByTenantIdAndEnabled(tenantId, e, pageable))
                .orElseGet(() -> ruleRepository.findByTenantId(tenantId, pageable));

        return PageResponse.of(page.map(toRuleResponse::apply));
    }

    @Transactional
    public AlertRuleResponse updateRule(String tenantId, UUID ruleId, AlertRuleRequest request) {
        return findRuleOrThrow(tenantId, ruleId)
                .map(applyUpdate(request))
                .map(ruleRepository::save)
                .map(toRuleResponse)
                .orElseThrow(notFound("AlertRule", ruleId));
    }

    @Transactional
    public void toggleRule(String tenantId, UUID ruleId, boolean enabled) {
        findRuleOrThrow(tenantId, ruleId)
                .ifPresentOrElse(
                        toggleEnabled(enabled),
                        () -> { throw new ResourceNotFoundException("AlertRule", ruleId); }
                );
    }

    // ──────────── Alert Evaluation ────────────

    /**
     * Evaluates all active rules for a given job and context.
     * Uses functional dispatch via conditionEvaluators map — no if/else chains.
     *
     * @return list of triggered alert history entries
     */
    @Transactional
    @Timed(value = "alert.evaluate", description = "Time to evaluate alert rules")
    public List<AlertHistoryResponse> evaluateRules(String tenantId, UUID jobId,
                                                     Map<String, Object> context) {
        var activeRules = ruleRepository.findActiveRulesForJob(tenantId, jobId);

        return activeRules.stream()
                .filter(rule -> isNotInCooldown(rule))
                .filter(rule -> evaluateCondition(rule, context))
                .map(rule -> triggerAlert(rule, tenantId, jobId, context))
                .map(toHistoryResponse)
                .collect(Collectors.toList());
    }

    // ──────────── History ────────────

    public PageResponse<AlertHistoryResponse> listHistory(String tenantId,
                                                           Instant from, Instant to,
                                                           Pageable pageable) {
        var page = historyRepository.findByTenantIdAndTriggeredAtBetween(
                tenantId, from, to, pageable);
        return PageResponse.of(page.map(toHistoryResponse::apply));
    }

    @Transactional
    public AlertHistoryResponse acknowledgeAlert(String tenantId, UUID alertId,
                                                  Instant triggeredAt, String acknowledgedBy) {
        var compositeId = new com.jobmonitor.alerting.entity.AlertHistoryId(alertId, triggeredAt);
        return historyRepository.findById(compositeId)
                .filter(h -> h.getTenantId().equals(tenantId))
                .map(acknowledge(acknowledgedBy))
                .map(historyRepository::save)
                .map(toHistoryResponse)
                .orElseThrow(notFound("AlertHistory", alertId));
    }

    // ──────────── Private Functional Helpers ────────────

    private Optional<AlertRule> findRuleOrThrow(String tenantId, UUID ruleId) {
        return ruleRepository.findByIdAndTenantId(ruleId, tenantId);
    }

    /** Returns a UnaryOperator that applies updates to an existing rule. */
    private UnaryOperator<AlertRule> applyUpdate(AlertRuleRequest request) {
        return existing -> {
            alertMapper.updateRuleEntity(request, existing);
            uniqueNameValidator.validate(existing);
            return existing;
        };
    }

    /** Returns a Consumer that toggles the enabled flag and persists. */
    private Consumer<AlertRule> toggleEnabled(boolean enabled) {
        return rule -> {
            rule.setEnabled(enabled);
            ruleRepository.save(rule);
            log.info("Alert rule id={} enabled={}", rule.getId(), enabled);
        };
    }

    /** Returns a UnaryOperator that marks an alert as acknowledged. */
    private UnaryOperator<AlertHistory> acknowledge(String acknowledgedBy) {
        return history -> {
            history.setStatus("ACKNOWLEDGED");
            history.setAcknowledgedBy(acknowledgedBy);
            history.setAcknowledgedAt(Instant.now());
            return history;
        };
    }

    /** Evaluates the condition for a rule using the functional dispatch registry. */
    private boolean evaluateCondition(AlertRule rule, Map<String, Object> context) {
        return Optional.ofNullable(conditionEvaluators.get(rule.getRuleType()))
                .map(evaluator -> evaluator.evaluate(rule.getConditionJson(), context))
                .orElseGet(() -> {
                    log.warn("No evaluator registered for rule type '{}', skipping", rule.getRuleType());
                    return false;
                });
    }

    /** Checks if a rule is still within its cooldown period since last trigger. */
    private boolean isNotInCooldown(AlertRule rule) {
        return historyRepository.findFirstByAlertRuleIdOrderByTriggeredAtDesc(rule.getId())
                .map(AlertHistory::getTriggeredAt)
                .map(lastTriggered -> {
                    var cooldown = Duration.ofSeconds(
                            Optional.ofNullable(rule.getCooldownSeconds()).orElse(300));
                    return Instant.now().isAfter(lastTriggered.plus(cooldown));
                })
                .orElse(true); // No history = never triggered = not in cooldown
    }

    /** Triggers an alert: creates history entry + publishes event. */
    private AlertHistory triggerAlert(AlertRule rule, String tenantId, UUID jobId,
                                      Map<String, Object> context) {
        var history = AlertHistory.builder()
                .alertRule(rule)
                .tenantId(tenantId)
                .jobId(jobId)
                .severity(rule.getSeverity())
                .status("TRIGGERED")
                .message(buildMessage(rule, context))
                .contextJson(context.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()))))
                .build();

        var saved = historyRepository.save(history);

        publishAlertEvent(rule, AlertEvent.Action.TRIGGERED, builder ->
                builder.alertId(saved.getId())
                        .severity(AlertEvent.Severity.valueOf(rule.getSeverity()))
                        .description(saved.getMessage()));

        log.info("Alert triggered: rule={}, severity={}, job={}", rule.getName(), rule.getSeverity(), jobId);
        return saved;
    }

    /** Builds alert message from rule + context — functional string construction. */
    private String buildMessage(AlertRule rule, Map<String, Object> context) {
        var contextSummary = context.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return String.format("Alert '%s' (%s) triggered. Context: [%s]",
                rule.getName(), rule.getRuleType(), contextSummary);
    }

    /** Factory for not-found exception suppliers. */
    private Supplier<ResourceNotFoundException> notFound(String type, Object id) {
        return () -> new ResourceNotFoundException(type, id);
    }

    /** Extracts a numeric threshold from condition JSON string. */
    private Optional<Long> extractThreshold(String conditionJson) {
        return Optional.ofNullable(conditionJson)
                .filter(json -> json.contains("threshold"))
                .map(json -> {
                    // Simple extraction — production would use Jackson ObjectMapper
                    var idx = json.indexOf("threshold");
                    var colonIdx = json.indexOf(':', idx);
                    var commaIdx = json.indexOf(',', colonIdx);
                    var braceIdx = json.indexOf('}', colonIdx);
                    var endIdx = commaIdx > 0 ? Math.min(commaIdx, braceIdx) : braceIdx;
                    return json.substring(colonIdx + 1, endIdx).trim();
                })
                .map(String::trim)
                .flatMap(s -> {
                    try {
                        return Optional.of(Long.parseLong(s));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    /** Extracts a numeric value from the context map. */
    private Optional<Long> getContextValue(Map<String, Object> context, String key) {
        return Optional.ofNullable(context.get(key))
                .map(Object::toString)
                .flatMap(s -> {
                    try {
                        return Optional.of(Long.parseLong(s));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    /** Publishes an alert event with customizable builder. */
    private void publishAlertEvent(AlertRule rule, AlertEvent.Action action,
                                    Consumer<AlertEvent.AlertEventBuilder> customizer) {
        var builder = AlertEvent.builder()
                .tenantId(rule.getTenantId())
                .ruleId(rule.getId())
                .action(action)
                .alertName(rule.getName())
                .channels(rule.getNotificationChannels().toArray(String[]::new));

        customizer.accept(builder);

        eventPublisher.publish(alertEventsTopic.get(), rule.getTenantId(), builder.build());
    }
}
