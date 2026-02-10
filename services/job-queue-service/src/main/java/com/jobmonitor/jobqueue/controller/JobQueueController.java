package com.jobmonitor.jobqueue.controller;

import com.jobmonitor.jobqueue.dto.EnqueueRequest;
import com.jobmonitor.jobqueue.dto.QueueItemResponse;
import com.jobmonitor.jobqueue.dto.QueueStatsResponse;
import com.jobmonitor.jobqueue.service.JobQueueService;
import com.jobmonitor.jobqueue.service.QueueStatsService;
import com.jobmonitor.platform.common.dto.ApiResponse;
import com.jobmonitor.platform.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Job queue controller — enqueue, claim, complete, fail, cancel, stats.
 */
@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class JobQueueController {

    private final JobQueueService queueService;
    private final QueueStatsService statsService;

    @PostMapping("/enqueue")
    public ResponseEntity<ApiResponse<QueueItemResponse>> enqueue(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody EnqueueRequest request) {
        var response = queueService.enqueue(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<QueueItemResponse>> claimNext(
            @RequestHeader("X-Worker-Id") String workerId) {
        return queueService.claimNext(workerId)
                .map(item -> ResponseEntity.ok(ApiResponse.ok(item)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{itemId}/complete")
    public ResponseEntity<ApiResponse<QueueItemResponse>> complete(@PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok(queueService.complete(itemId)));
    }

    @PostMapping("/{itemId}/fail")
    public ResponseEntity<ApiResponse<QueueItemResponse>> fail(
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "Unknown error") String errorMessage) {
        return ResponseEntity.ok(ApiResponse.ok(queueService.fail(itemId, errorMessage)));
    }

    @PostMapping("/{itemId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID itemId) {
        queueService.cancel(itemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<PageResponse<QueueItemResponse>> listItems(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queueService.listByTenant(tenantId, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.of(result));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<PageResponse<QueueItemResponse>> listByStatus(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queueService.listByStatus(tenantId, status, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.of(result));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<QueueStatsResponse>> getStats(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(statsService.getStats(tenantId)));
    }
}
