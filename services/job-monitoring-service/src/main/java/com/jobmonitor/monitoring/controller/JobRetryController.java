package com.jobmonitor.monitoring.controller;

import com.jobmonitor.monitoring.service.JobRetryService;
import com.jobmonitor.platform.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoint for manually retrying a failed job execution.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobRetryController {

    private final JobRetryService retryService;

    @PostMapping("/{jobId}/retry")
    public ResponseEntity<ApiResponse<String>> retryJob(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable UUID jobId) {
        retryService.retryFailedJob(tenantId, jobId);
        return ResponseEntity.ok(ApiResponse.ok("Retry initiated for job " + jobId));
    }
}
