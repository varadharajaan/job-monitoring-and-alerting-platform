package com.jobmonitor.alerting.controller;

import com.jobmonitor.alerting.dto.*;
import com.jobmonitor.alerting.service.AlertService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for alert rule management and history.
 * Tenant ID extracted from header (set by gateway after JWT validation).
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final AlertService alertService;

    // ──────────── Alert Rules ────────────

    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<AlertRuleResponse>> createRule(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @Valid @RequestBody AlertRuleRequest request) {

        var response = alertService.createRule(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Alert rule created successfully"));
    }

    @GetMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponse<AlertRuleResponse>> getRule(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID ruleId) {

        var response = alertService.getRule(tenantId, ruleId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<PageResponse<AlertRuleResponse>>> listRules(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        var page = alertService.listRules(tenantId, Optional.ofNullable(enabled), pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponse<AlertRuleResponse>> updateRule(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody AlertRuleRequest request) {

        var response = alertService.updateRule(tenantId, ruleId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Alert rule updated successfully"));
    }

    @PatchMapping("/rules/{ruleId}/toggle")
    public ResponseEntity<ApiResponse<Void>> toggleRule(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID ruleId,
            @RequestParam boolean enabled) {

        alertService.toggleRule(tenantId, ruleId, enabled);
        var msg = enabled ? "Alert rule enabled" : "Alert rule disabled";
        return ResponseEntity.ok(ApiResponse.ok(null, msg));
    }

    // ──────────── Alert Evaluation ────────────

    @PostMapping("/evaluate/{jobId}")
    public ResponseEntity<ApiResponse<List<AlertHistoryResponse>>> evaluateRules(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID jobId,
            @RequestBody Map<String, Object> context) {

        var triggered = alertService.evaluateRules(tenantId, jobId, context);
        return ResponseEntity.ok(ApiResponse.ok(triggered,
                String.format("%d alert(s) triggered", triggered.size())));
    }

    // ──────────── Alert History ────────────

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<AlertHistoryResponse>>> listHistory(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "triggeredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        var effectiveFrom = Optional.ofNullable(from)
                .orElseGet(() -> Instant.now().minus(7, ChronoUnit.DAYS));
        var effectiveTo = Optional.ofNullable(to)
                .orElseGet(Instant::now);

        var page = alertService.listHistory(tenantId, effectiveFrom, effectiveTo, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @PatchMapping("/history/{alertId}/acknowledge")
    public ResponseEntity<ApiResponse<AlertHistoryResponse>> acknowledgeAlert(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID alertId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant triggeredAt,
            @RequestParam String acknowledgedBy) {

        var response = alertService.acknowledgeAlert(tenantId, alertId, triggeredAt, acknowledgedBy);
        return ResponseEntity.ok(ApiResponse.ok(response, "Alert acknowledged"));
    }
}
