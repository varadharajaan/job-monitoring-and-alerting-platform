package com.jobmonitor.logingest.controller;

import com.jobmonitor.logingest.model.LogRetentionPolicy;
import com.jobmonitor.logingest.service.LogRetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for managing log retention policies.
 */
@RestController
@RequestMapping("/api/v1/logs/retention")
@RequiredArgsConstructor
@Tag(name = "Log Retention", description = "Per-tenant log retention policies")
public class LogRetentionController {

    private final LogRetentionService retentionService;

    @PostMapping
    @Operation(summary = "Create or update retention policy")
    public ResponseEntity<LogRetentionPolicy> createOrUpdate(@RequestBody LogRetentionPolicy policy) {
        return ResponseEntity.ok(retentionService.createOrUpdate(policy));
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "Get retention policy for tenant")
    public ResponseEntity<LogRetentionPolicy> getForTenant(@PathVariable String tenantId) {
        return retentionService.getForTenant(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete retention policy")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        retentionService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }
}
