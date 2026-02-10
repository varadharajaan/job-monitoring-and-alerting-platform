package com.jobmonitor.notification.service;

import com.jobmonitor.notification.dto.*;
import com.jobmonitor.notification.entity.Notification;
import com.jobmonitor.notification.entity.NotificationTemplate;
import com.jobmonitor.notification.mapper.NotificationMapper;
import com.jobmonitor.notification.repository.NotificationRepository;
import com.jobmonitor.notification.repository.NotificationTemplateRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.dto.PageResponse;
import com.jobmonitor.platform.common.event.NotificationEvent;
import com.jobmonitor.platform.common.event.PlatformEvent;
import com.jobmonitor.platform.common.exception.BusinessException;
import com.jobmonitor.platform.common.exception.DuplicateResourceException;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.functional.EventPublisher;
import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Notification service with functional channel dispatch, template rendering,
 * rate limiting, and extensive Optional/lambda usage.
 * <p>
 * The channel dispatcher uses a {@code Map<String, NotificationDispatcher>}
 * to route notifications to the correct handler — no switch statements.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationMapper notificationMapper;
    private final PlatformProperties properties;
    private final EventPublisher<PlatformEvent> eventPublisher;

    // ──────────── Functional Dispatch ────────────

    /** Channel dispatchers — each entry is a NotificationDispatcher lambda. */
    private final Map<String, NotificationDispatcher> channelDispatchers;

    /** Transforms entities to response DTOs. */
    private final Function<NotificationTemplate, TemplateResponse> toTemplateResponse;
    private final Function<Notification, NotificationResponse> toNotificationResponse;

    /** Resolves Kafka topic from config — zero hardcoded strings. */
    private final Supplier<String> notificationEventsTopic;

    /** Rate limit lookup — resolves per-channel limit from config. */
    private final Function<String, Integer> rateLimitForChannel;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationTemplateRepository templateRepository,
                                NotificationMapper notificationMapper,
                                PlatformProperties properties,
                                EventPublisher<PlatformEvent> eventPublisher,
                                Map<String, NotificationDispatcher> channelDispatchers) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.notificationMapper = notificationMapper;
        this.properties = properties;
        this.eventPublisher = eventPublisher;

        // Functional references
        this.toTemplateResponse = notificationMapper::toTemplateResponse;
        this.toNotificationResponse = notificationMapper::toNotificationResponse;
        this.notificationEventsTopic = () -> properties.getKafka().getTopics().getNotificationEvents();

        // Rate limit resolver — maps channel name to config limit via functional dispatch
        var rateLimits = properties.getNotification().getRateLimit();
        this.rateLimitForChannel = channel -> Map.of(
                "EMAIL", rateLimits.getEmail(),
                "SMS", rateLimits.getSms(),
                "SLACK", rateLimits.getSlack(),
                "PUSH", rateLimits.getPush()
        ).getOrDefault(channel.toUpperCase(), rateLimits.getEmail());

        // Channel dispatchers — injected from ChannelDispatcherConfig
        this.channelDispatchers = channelDispatchers;
    }

    // ──────────── Template CRUD ────────────

    @Transactional
    public TemplateResponse createTemplate(String tenantId, TemplateRequest request) {
        log.info("Creating template '{}' channel={} for tenant={}", request.getName(),
                request.getChannel(), tenantId);

        // Duplicate check first — avoid unnecessary entity creation
        templateRepository.findByTenantIdAndNameAndChannel(tenantId, request.getName(), request.getChannel())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("NotificationTemplate",
                            request.getName() + "/" + request.getChannel());
                });

        var template = notificationMapper.toTemplateEntity(request);
        template.setTenantId(tenantId);

        var saved = templateRepository.save(template);
        return toTemplateResponse.apply(saved);
    }

    public TemplateResponse getTemplate(String tenantId, UUID templateId) {
        return findTemplateOrThrow(tenantId, templateId)
                .map(toTemplateResponse)
                .orElseThrow(notFound("NotificationTemplate", templateId));
    }

    public PageResponse<TemplateResponse> listTemplates(String tenantId, Optional<String> channel,
                                                         Pageable pageable) {
        var page = channel
                .filter(c -> !c.isBlank())
                .map(c -> templateRepository.findByTenantIdAndChannel(tenantId, c, pageable))
                .orElseGet(() -> templateRepository.findByTenantId(tenantId, pageable));

        return PageResponse.of(page.map(toTemplateResponse::apply));
    }

    @Transactional
    public TemplateResponse updateTemplate(String tenantId, UUID templateId,
                                            TemplateRequest request) {
        return findTemplateOrThrow(tenantId, templateId)
                .map(applyTemplateUpdate(request))
                .map(templateRepository::save)
                .map(toTemplateResponse)
                .orElseThrow(notFound("NotificationTemplate", templateId));
    }

    // ──────────── Send Notification ────────────

    @Transactional
    public NotificationResponse sendNotification(String tenantId, SendNotificationRequest request) {
        log.info("Sending {} notification to {} for tenant={}",
                request.getChannel(), request.getRecipient(), tenantId);

        // Rate limit check via functional composition
        checkRateLimit(tenantId, request.getChannel());

        // Resolve body from template or direct body
        var resolvedBody = resolveBody(tenantId, request);
        var resolvedSubject = resolveSubject(tenantId, request);

        var notification = Notification.builder()
                .tenantId(tenantId)
                .channel(request.getChannel().toUpperCase())
                .recipient(request.getRecipient())
                .subject(resolvedSubject)
                .body(resolvedBody)
                .status("PENDING")
                .priority(Optional.ofNullable(request.getPriority()).orElse(5))
                .metadata(Optional.ofNullable(request.getMetadata()).orElse(Map.of()))
                .scheduledAt(request.getScheduledAt())
                .build();

        // Associate template if provided
        Optional.ofNullable(request.getTemplateId())
                .flatMap(id -> templateRepository.findByIdAndTenantId(id, tenantId))
                .ifPresent(notification::setTemplate);

        var saved = notificationRepository.save(notification);

        // Dispatch via functional channel registry
        dispatchNotification(saved);

        publishNotificationEvent(saved, NotificationEvent.Action.REQUESTED, builder -> {});

        return toNotificationResponse.apply(saved);
    }

    public PageResponse<NotificationResponse> listNotifications(String tenantId, Optional<String> channel,
                                                                  Instant from, Instant to,
                                                                  Pageable pageable) {
        var page = channel
                .filter(c -> !c.isBlank())
                .map(c -> notificationRepository.findByTenantIdAndChannelAndCreatedAtBetween(
                        tenantId, c, from, to, pageable))
                .orElseGet(() -> notificationRepository.findByTenantIdAndCreatedAtBetween(
                        tenantId, from, to, pageable));

        return PageResponse.of(page.map(toNotificationResponse::apply));
    }

    // ──────────── Private Functional Helpers ────────────

    private Optional<NotificationTemplate> findTemplateOrThrow(String tenantId, UUID templateId) {
        return templateRepository.findByIdAndTenantId(templateId, tenantId);
    }

    /** Returns a UnaryOperator that applies template updates. */
    private UnaryOperator<NotificationTemplate> applyTemplateUpdate(TemplateRequest request) {
        return existing -> {
            notificationMapper.updateTemplateEntity(request, existing);
            return existing;
        };
    }

    /** Resolves the notification body — template rendering or direct body. */
    private String resolveBody(String tenantId, SendNotificationRequest request) {
        return Optional.ofNullable(request.getTemplateId())
                .flatMap(id -> templateRepository.findByIdAndTenantId(id, tenantId))
                .map(NotificationTemplate::getBody)
                .map(template -> renderTemplate(template,
                        Optional.ofNullable(request.getTemplateVariables()).orElse(Map.of())))
                .orElseGet(() -> Optional.ofNullable(request.getBody()).orElse(""));
    }

    /** Resolves the notification subject — template or direct. */
    private String resolveSubject(String tenantId, SendNotificationRequest request) {
        return Optional.ofNullable(request.getTemplateId())
                .flatMap(id -> templateRepository.findByIdAndTenantId(id, tenantId))
                .map(NotificationTemplate::getSubject)
                .map(subject -> renderTemplate(subject,
                        Optional.ofNullable(request.getTemplateVariables()).orElse(Map.of())))
                .orElseGet(() -> Optional.ofNullable(request.getSubject()).orElse(""));
    }

    /**
     * Renders a template string by replacing {{variable}} placeholders
     * with values from the variables map — functional string processing.
     */
    private String renderTemplate(String template, Map<String, Object> variables) {
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            var varName = matcher.group(1);
            var replacement = Optional.ofNullable(variables.get(varName))
                    .map(Object::toString)
                    .orElse("{{" + varName + "}}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Dispatches notification via the functional channel registry. */
    private void dispatchNotification(Notification notification) {
        Optional.ofNullable(channelDispatchers.get(notification.getChannel().toUpperCase()))
                .ifPresentOrElse(
                        dispatcher -> {
                            try {
                                var providerId = dispatcher.dispatch(
                                        notification.getRecipient(),
                                        Optional.ofNullable(notification.getSubject()).orElse(""),
                                        Optional.ofNullable(notification.getBody()).orElse(""),
                                        notification.getMetadata());
                                notification.setStatus("SENT");
                                notification.setSentAt(Instant.now());
                                notificationRepository.save(notification);
                                log.info("Notification sent via {} providerId={}", notification.getChannel(), providerId);
                            } catch (Exception e) {
                                notification.setStatus("FAILED");
                                notification.setErrorMessage(e.getMessage());
                                notification.setRetryCount(notification.getRetryCount() + 1);
                                notificationRepository.save(notification);
                                log.error("Failed to dispatch notification id={}: {}",
                                        notification.getId(), e.getMessage());
                            }
                        },
                        () -> {
                            log.warn("No dispatcher registered for channel '{}', marking as FAILED",
                                    notification.getChannel());
                            notification.setStatus("FAILED");
                            notification.setErrorMessage("Unsupported channel: " + notification.getChannel());
                            notificationRepository.save(notification);
                        }
                );
    }

    /** Checks rate limit for a channel — throws BusinessException if exceeded. */
    private void checkRateLimit(String tenantId, String channel) {
        var limit = rateLimitForChannel.apply(channel);
        var since = Instant.now().minus(1, ChronoUnit.HOURS);
        var count = notificationRepository.countByTenantIdAndChannelSince(
                tenantId, channel.toUpperCase(), since);

        Optional.of(count)
                .filter(c -> c >= limit)
                .ifPresent(c -> {
                    throw new BusinessException(
                            String.format("Rate limit exceeded for %s: %d/%d per hour",
                                    channel, count, limit));
                });
    }

    /** Factory for not-found exception suppliers. */
    private Supplier<ResourceNotFoundException> notFound(String type, Object id) {
        return () -> new ResourceNotFoundException(type, id);
    }

    /** Publishes a notification event with a customizable builder. */
    private void publishNotificationEvent(Notification notification,
                                           NotificationEvent.Action action,
                                           Consumer<NotificationEvent.NotificationEventBuilder> customizer) {
        var builder = NotificationEvent.builder()
                .tenantId(notification.getTenantId())
                .notificationId(notification.getId())
                .action(action)
                .channel(NotificationEvent.Channel.valueOf(notification.getChannel()))
                .recipient(notification.getRecipient())
                .subject(notification.getSubject());

        customizer.accept(builder);

        eventPublisher.publish(notificationEventsTopic.get(),
                notification.getTenantId(), builder.build());
    }
}
