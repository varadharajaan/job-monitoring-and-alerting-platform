package com.jobmonitor.gateway.controller;

import com.jobmonitor.gateway.dto.IngestionRequest;
import com.jobmonitor.gateway.dto.IngestionResponse;
import com.jobmonitor.gateway.service.IngestionService;
import com.jobmonitor.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Ingestion gateway controller — rate-limited entry point for job events.
 */
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<IngestionResponse>> ingestEvent(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody IngestionRequest request) {
        IngestionResponse response = ingestionService.ingest(tenantId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<String>> ingestBatch(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody java.util.List<IngestionRequest> requests) {
        requests.forEach(request -> ingestionService.ingest(tenantId, request));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Accepted " + requests.size() + " events"));
    }
}
