package com.jobmonitor.logingest.controller;

import com.jobmonitor.logingest.model.LogAlertPattern;
import com.jobmonitor.logingest.service.LogAlertPatternService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing log alert patterns.
 */
@RestController
@RequestMapping("/api/v1/logs/patterns")
@RequiredArgsConstructor
@Tag(name = "Log Alert Patterns", description = "CRUD for regex-based log alert patterns")
public class LogAlertPatternController {

    private final LogAlertPatternService patternService;

    @PostMapping
    @Operation(summary = "Create pattern", description = "Create a new log alert pattern")
    public ResponseEntity<LogAlertPattern> create(@RequestBody LogAlertPattern pattern) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patternService.createPattern(pattern));
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "List patterns", description = "List all active patterns for a tenant")
    public ResponseEntity<List<LogAlertPattern>> listByTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(patternService.getPatternsForTenant(tenantId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get pattern", description = "Get a single log alert pattern by ID")
    public ResponseEntity<LogAlertPattern> getById(@PathVariable UUID id) {
        return patternService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update pattern", description = "Update an existing log alert pattern")
    public ResponseEntity<LogAlertPattern> update(@PathVariable UUID id, @RequestBody LogAlertPattern pattern) {
        return ResponseEntity.ok(patternService.updatePattern(id, pattern));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete pattern", description = "Delete a log alert pattern")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        patternService.deletePattern(id);
        return ResponseEntity.noContent().build();
    }
}
