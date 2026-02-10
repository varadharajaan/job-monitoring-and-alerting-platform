package com.jobmonitor.dbperf.controller;

import com.jobmonitor.dbperf.model.SlowQuery;
import com.jobmonitor.dbperf.service.SlowQueryMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/slow-queries")
@RequiredArgsConstructor
@Tag(name = "Slow Queries", description = "View and manage captured slow queries")
public class SlowQueryController {

    private final SlowQueryMonitorService slowQueryService;

    @GetMapping("/database/{databaseId}")
    @Operation(summary = "List slow queries for a database (paginated)")
    public ResponseEntity<Page<SlowQuery>> getForDatabase(
            @PathVariable UUID databaseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(slowQueryService.getSlowQueriesForDatabase(databaseId, page, size));
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "List all slow queries for a tenant")
    public ResponseEntity<List<SlowQuery>> getForTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(slowQueryService.getSlowQueriesForTenant(tenantId));
    }
}
