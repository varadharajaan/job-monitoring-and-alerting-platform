package com.jobmonitor.dbperf.service;

import com.jobmonitor.dbperf.dto.ExplainPlanResult;
import com.jobmonitor.dbperf.model.MonitoredDatabase;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service that runs EXPLAIN ANALYZE on queries against monitored databases
 * and returns structured plan output with cost estimation.
 */
@Service
@Slf4j
public class ExplainAnalyzeService {

    /**
     * Executes EXPLAIN (ANALYZE, COSTS, VERBOSE, BUFFERS, FORMAT JSON) for a query.
     */
    @Timed(value = "dbperf.explain.analyze", description = "EXPLAIN ANALYZE execution time")
    public ExplainPlanResult explainAnalyze(MonitoredDatabase db, String query) {
        JdbcTemplate jt = createJdbcTemplate(db);

        // Use EXPLAIN with JSON format for structured output
        String explainSql = "EXPLAIN (ANALYZE, COSTS, VERBOSE, BUFFERS, FORMAT JSON) " + query;

        try {
            List<Map<String, Object>> rows = jt.queryForList(explainSql);
            StringBuilder planJson = new StringBuilder();
            for (Map<String, Object> row : rows) {
                planJson.append(row.values().iterator().next().toString());
            }

            // Also get estimated cost without ANALYZE
            String costSql = "EXPLAIN (COSTS, FORMAT JSON) " + query;
            List<Map<String, Object>> costRows = jt.queryForList(costSql);
            StringBuilder costJson = new StringBuilder();
            for (Map<String, Object> row : costRows) {
                costJson.append(row.values().iterator().next().toString());
            }

            return ExplainPlanResult.builder()
                    .query(query)
                    .databaseName(db.getName())
                    .planJson(planJson.toString())
                    .costEstimateJson(costJson.toString())
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("EXPLAIN ANALYZE failed for database={}: {}", db.getName(), e.getMessage());
            return ExplainPlanResult.builder()
                    .query(query)
                    .databaseName(db.getName())
                    .errorMessage(e.getMessage())
                    .success(false)
                    .build();
        }
    }

    /**
     * Estimates query cost without executing (no ANALYZE — read-only).
     */
    @Timed(value = "dbperf.explain.cost", description = "Cost estimation time")
    public ExplainPlanResult estimateCost(MonitoredDatabase db, String query) {
        JdbcTemplate jt = createJdbcTemplate(db);

        try {
            String costSql = "EXPLAIN (COSTS, FORMAT JSON) " + query;
            List<Map<String, Object>> rows = jt.queryForList(costSql);
            StringBuilder json = new StringBuilder();
            for (Map<String, Object> row : rows) {
                json.append(row.values().iterator().next().toString());
            }

            return ExplainPlanResult.builder()
                    .query(query)
                    .databaseName(db.getName())
                    .costEstimateJson(json.toString())
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("Cost estimation failed for database={}: {}", db.getName(), e.getMessage());
            return ExplainPlanResult.builder()
                    .query(query)
                    .databaseName(db.getName())
                    .errorMessage(e.getMessage())
                    .success(false)
                    .build();
        }
    }

    /**
     * Provides query rewrite suggestions based on common anti-patterns.
     */
    public List<String> suggestRewrites(String query) {
        List<String> suggestions = new java.util.ArrayList<>();
        String upper = query.toUpperCase();

        if (upper.contains("SELECT *")) {
            suggestions.add("Replace SELECT * with explicit column names to reduce I/O and network transfer.");
        }
        if (upper.contains("NOT IN")) {
            suggestions.add("Consider replacing NOT IN with NOT EXISTS or LEFT JOIN ... IS NULL for better performance with NULLs.");
        }
        if (upper.contains("LIKE '%")) {
            suggestions.add("Leading wildcard in LIKE prevents index usage. Consider full-text search or reverse index for prefix matching.");
        }
        if (upper.contains("OR") && upper.contains("WHERE")) {
            suggestions.add("Multiple OR conditions may prevent index usage. Consider UNION ALL of separate queries or use IN clause.");
        }
        if (!upper.contains("LIMIT") && upper.contains("ORDER BY")) {
            suggestions.add("Add LIMIT clause when using ORDER BY to avoid sorting the entire result set.");
        }
        if (upper.contains("DISTINCT")) {
            suggestions.add("DISTINCT can be expensive. Verify it's necessary — it may indicate a JOIN issue.");
        }
        if (upper.contains("HAVING") && !upper.contains("GROUP BY")) {
            suggestions.add("HAVING without GROUP BY is unusual. Verify query logic.");
        }

        return suggestions;
    }

    private JdbcTemplate createJdbcTemplate(MonitoredDatabase db) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(db.getJdbcUrl());
        ds.setUsername(db.getUsername());
        ds.setPassword(db.getEncryptedPassword());
        return new JdbcTemplate(ds);
    }
}
