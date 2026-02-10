package com.jobmonitor.dbperf.controller;

import com.jobmonitor.dbperf.model.MonitoredDatabase;
import com.jobmonitor.dbperf.service.MonitoredDatabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/databases")
@RequiredArgsConstructor
@Tag(name = "Monitored Databases", description = "CRUD for database connections to monitor")
public class MonitoredDatabaseController {

    private final MonitoredDatabaseService dbService;

    @PostMapping
    @Operation(summary = "Register a database for monitoring")
    public ResponseEntity<MonitoredDatabase> register(@RequestBody MonitoredDatabase db) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dbService.register(db));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get monitored database by ID")
    public ResponseEntity<MonitoredDatabase> getById(@PathVariable UUID id) {
        return dbService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "List databases for a tenant")
    public ResponseEntity<List<MonitoredDatabase>> listForTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(dbService.getForTenant(tenantId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update monitored database settings")
    public ResponseEntity<MonitoredDatabase> update(@PathVariable UUID id, @RequestBody MonitoredDatabase db) {
        return ResponseEntity.ok(dbService.update(id, db));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove database from monitoring")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        dbService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
