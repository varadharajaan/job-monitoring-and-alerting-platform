package com.jobmonitor.dbperf.controller;

import com.jobmonitor.dbperf.model.IndexSuggestion;
import com.jobmonitor.dbperf.service.IndexSuggestionService;
import com.jobmonitor.dbperf.service.MonitoredDatabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/index-suggestions")
@RequiredArgsConstructor
@Tag(name = "Index Suggestions", description = "View and manage index recommendations")
public class IndexSuggestionController {

    private final IndexSuggestionService suggestionService;
    private final MonitoredDatabaseService dbService;

    @PostMapping("/analyze/{databaseId}")
    @Operation(summary = "Analyze slow queries and generate index suggestions")
    public ResponseEntity<List<IndexSuggestion>> analyze(@PathVariable UUID databaseId) {
        return dbService.getById(databaseId)
                .map(db -> ResponseEntity.ok(suggestionService.analyzeAndSuggest(db)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/database/{databaseId}")
    @Operation(summary = "List pending index suggestions for a database")
    public ResponseEntity<List<IndexSuggestion>> getForDatabase(@PathVariable UUID databaseId) {
        return ResponseEntity.ok(suggestionService.getSuggestionsForDatabase(databaseId));
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "List all index suggestions for a tenant")
    public ResponseEntity<List<IndexSuggestion>> getForTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(suggestionService.getSuggestionsForTenant(tenantId));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update suggestion status (APPLIED, DISMISSED)")
    public ResponseEntity<IndexSuggestion> updateStatus(
            @PathVariable UUID id,
            @RequestParam IndexSuggestion.SuggestionStatus status) {
        return ResponseEntity.ok(suggestionService.updateStatus(id, status));
    }
}
