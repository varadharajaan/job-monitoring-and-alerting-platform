package com.jobmonitor.notification.controller;

import com.jobmonitor.notification.dto.*;
import com.jobmonitor.notification.service.NotificationService;
import com.jobmonitor.platform.common.dto.ApiResponse;
import com.jobmonitor.platform.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for notification management — templates + sending.
 * Tenant ID extracted from header (set by gateway after JWT validation).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final NotificationService notificationService;

    // ──────────── Templates ────────────

    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @Valid @RequestBody TemplateRequest request) {

        var response = notificationService.createTemplate(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Template created successfully"));
    }

    @GetMapping("/templates/{templateId}")
    public ResponseEntity<ApiResponse<TemplateResponse>> getTemplate(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID templateId) {

        var response = notificationService.getTemplate(tenantId, templateId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<PageResponse<TemplateResponse>>> listTemplates(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @RequestParam(required = false) String channel,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        var page = notificationService.listTemplates(tenantId, Optional.ofNullable(channel), pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @PutMapping("/templates/{templateId}")
    public ResponseEntity<ApiResponse<TemplateResponse>> updateTemplate(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID templateId,
            @Valid @RequestBody TemplateRequest request) {

        var response = notificationService.updateTemplate(tenantId, templateId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Template updated successfully"));
    }

    // ──────────── Sending ────────────

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<NotificationResponse>> sendNotification(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @Valid @RequestBody SendNotificationRequest request) {

        var response = notificationService.sendNotification(tenantId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(response, "Notification queued for delivery"));
    }

    // ──────────── History ────────────

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> listNotifications(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        var effectiveFrom = Optional.ofNullable(from)
                .orElseGet(() -> Instant.now().minus(7, ChronoUnit.DAYS));
        var effectiveTo = Optional.ofNullable(to)
                .orElseGet(Instant::now);

        var page = notificationService.listNotifications(tenantId, Optional.ofNullable(channel),
                effectiveFrom, effectiveTo, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
