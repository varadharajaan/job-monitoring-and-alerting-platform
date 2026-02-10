package com.jobmonitor.monitoring.controller;

import com.jobmonitor.monitoring.dto.*;
import com.jobmonitor.monitoring.service.JobService;
import com.jobmonitor.platform.common.dto.ApiResponse;
import com.jobmonitor.platform.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for job monitoring endpoints.
 * Tenant ID extracted from header (set by gateway after JWT validation).
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @Valid @RequestBody JobRequest request) {

        var response = jobService.createJob(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Job registered successfully"));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID jobId) {

        var response = jobService.getJob(tenantId, jobId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<JobResponse>>> listJobs(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        var page = jobService.listJobs(tenantId, Optional.ofNullable(status), pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID jobId,
            @Valid @RequestBody JobRequest request) {

        var response = jobService.updateJob(tenantId, jobId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Job updated successfully"));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<ApiResponse<Void>> deactivateJob(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID jobId) {

        jobService.deactivateJob(tenantId, jobId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Job deactivated successfully"));
    }

    @PostMapping("/{jobId}/executions")
    public ResponseEntity<ApiResponse<ExecutionResponse>> recordExecution(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID jobId,
            @Valid @RequestBody ExecutionRequest request) {

        var response = jobService.recordExecution(tenantId, jobId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Execution recorded"));
    }

    @GetMapping("/{jobId}/executions")
    public ResponseEntity<ApiResponse<PageResponse<ExecutionResponse>>> listExecutions(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable UUID jobId,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        var page = jobService.listExecutions(tenantId, jobId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
