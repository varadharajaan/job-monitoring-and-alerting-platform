package com.jobmonitor.logingest.controller;

import com.jobmonitor.logingest.dto.LogSearchRequest;
import com.jobmonitor.logingest.dto.LogSearchResponse;
import com.jobmonitor.logingest.model.LogEntry;
import com.jobmonitor.logingest.service.LogSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for log search and ingestion.
 */
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Tag(name = "Log Search", description = "Full-text search and ingestion of log entries")
public class LogSearchController {

    private final LogSearchService logSearchService;

    @PostMapping("/search")
    @Operation(summary = "Search logs", description = "Full-text search across tenant log indices with filters")
    public ResponseEntity<LogSearchResponse> search(@RequestBody LogSearchRequest request) {
        List<LogEntry> results = logSearchService.search(
                request.getTenantId(),
                request.getQuery(),
                request.getMinLevel(),
                request.getFrom(),
                request.getTo(),
                request.getPage(),
                request.getSize()
        );
        return ResponseEntity.ok(LogSearchResponse.builder()
                .results(results)
                .page(request.getPage())
                .size(request.getSize())
                .total(results.size())
                .build());
    }

    @PostMapping("/ingest")
    @Operation(summary = "Ingest a single log entry", description = "Index a log entry into Elasticsearch")
    public ResponseEntity<String> ingest(@RequestBody LogEntry entry) {
        String id = logSearchService.indexLogEntry(entry);
        return ResponseEntity.ok(id);
    }

    @PostMapping("/ingest/batch")
    @Operation(summary = "Batch ingest log entries", description = "Bulk-index multiple log entries")
    public ResponseEntity<Integer> batchIngest(@RequestBody List<LogEntry> entries) {
        int count = logSearchService.bulkIndex(entries);
        return ResponseEntity.ok(count);
    }
}
