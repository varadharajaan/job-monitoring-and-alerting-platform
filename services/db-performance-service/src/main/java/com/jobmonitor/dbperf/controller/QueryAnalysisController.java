package com.jobmonitor.dbperf.controller;

import com.jobmonitor.dbperf.dto.ExplainPlanResult;
import com.jobmonitor.dbperf.dto.QueryAnalysisRequest;
import com.jobmonitor.dbperf.service.ExplainAnalyzeService;
import com.jobmonitor.dbperf.service.MonitoredDatabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/query-analysis")
@RequiredArgsConstructor
@Tag(name = "Query Analysis", description = "EXPLAIN ANALYZE, cost estimation, and query rewrite suggestions")
public class QueryAnalysisController {

    private final ExplainAnalyzeService explainService;
    private final MonitoredDatabaseService dbService;

    @PostMapping("/explain")
    @Operation(summary = "Run EXPLAIN ANALYZE on a query")
    public ResponseEntity<ExplainPlanResult> explainAnalyze(@RequestBody QueryAnalysisRequest request) {
        return dbService.getById(request.getDatabaseId())
                .map(db -> {
                    ExplainPlanResult result;
                    if (request.isAnalyze()) {
                        result = explainService.explainAnalyze(db, request.getQuery());
                    } else {
                        result = explainService.estimateCost(db, request.getQuery());
                    }
                    result.setRewriteSuggestions(explainService.suggestRewrites(request.getQuery()));
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/suggest-rewrites")
    @Operation(summary = "Get query rewrite suggestions based on anti-pattern analysis")
    public ResponseEntity<List<String>> suggestRewrites(@RequestBody String query) {
        return ResponseEntity.ok(explainService.suggestRewrites(query));
    }
}
