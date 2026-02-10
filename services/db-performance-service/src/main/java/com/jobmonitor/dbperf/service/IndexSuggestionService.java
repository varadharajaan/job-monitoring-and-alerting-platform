package com.jobmonitor.dbperf.service;

import com.jobmonitor.dbperf.model.IndexSuggestion;
import com.jobmonitor.dbperf.model.MonitoredDatabase;
import com.jobmonitor.dbperf.model.SlowQuery;
import com.jobmonitor.dbperf.repository.IndexSuggestionRepository;
import com.jobmonitor.dbperf.repository.SlowQueryRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes slow queries to suggest missing indexes.
 * Uses a rule-based engine that parses WHERE/JOIN clauses from
 * slow queries and cross-references with existing indexes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexSuggestionService {

    private final IndexSuggestionRepository indexSuggestionRepository;
    private final SlowQueryRepository slowQueryRepository;

    private static final Pattern WHERE_COLUMN_PATTERN =
            Pattern.compile("(?:WHERE|AND|OR|JOIN\\s+\\w+\\s+ON)\\s+(?:\\w+\\.)?([a-zA-Z_]\\w*)\\s*(?:=|<|>|IN|LIKE|BETWEEN)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern TABLE_PATTERN =
            Pattern.compile("(?:FROM|JOIN|UPDATE|INTO)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    /**
     * Analyzes slow queries for a monitored database and generates index suggestions.
     */
    @Timed(value = "dbperf.index.analyze", description = "Index suggestion analysis time")
    public List<IndexSuggestion> analyzeAndSuggest(MonitoredDatabase db) {
        List<SlowQuery> slowQueries = slowQueryRepository.findByDatabaseIdAndQueryFingerprint(db.getId(), null);
        if (slowQueries.isEmpty()) {
            slowQueries = slowQueryRepository.findByTenantIdOrderByMeanTimeMsDesc(db.getTenantId());
        }

        Set<String> existingIndexes = getExistingIndexes(db);
        List<IndexSuggestion> suggestions = new ArrayList<>();

        for (SlowQuery sq : slowQueries) {
            List<String> tables = extractTables(sq.getQueryText());
            List<String> whereColumns = extractWhereColumns(sq.getQueryText());

            for (String table : tables) {
                for (String column : whereColumns) {
                    String indexKey = table + "." + column;
                    if (!existingIndexes.contains(indexKey.toLowerCase())) {
                        String ddl = String.format("CREATE INDEX idx_%s_%s ON %s (%s);",
                                table.toLowerCase(), column.toLowerCase(), table, column);

                        IndexSuggestion suggestion = IndexSuggestion.builder()
                                .databaseId(db.getId())
                                .tenantId(db.getTenantId())
                                .tableName(table)
                                .columnName(column)
                                .suggestedDdl(ddl)
                                .reason(String.format("Column '%s' used in WHERE/JOIN clause of slow query (mean=%.1fms, calls=%d)",
                                        column, sq.getMeanTimeMs(), sq.getCalls()))
                                .estimatedImprovementPct(estimateImprovement(sq))
                                .build();

                        suggestions.add(indexSuggestionRepository.save(suggestion));
                        existingIndexes.add(indexKey.toLowerCase()); // Avoid duplicates
                    }
                }
            }
        }

        log.info("Generated {} index suggestions for database={}", suggestions.size(), db.getName());
        return suggestions;
    }

    public List<IndexSuggestion> getSuggestionsForDatabase(UUID databaseId) {
        return indexSuggestionRepository.findByDatabaseIdAndStatus(databaseId, IndexSuggestion.SuggestionStatus.PENDING);
    }

    public List<IndexSuggestion> getSuggestionsForTenant(String tenantId) {
        return indexSuggestionRepository.findByTenantId(tenantId);
    }

    public IndexSuggestion updateStatus(UUID id, IndexSuggestion.SuggestionStatus status) {
        return indexSuggestionRepository.findById(id)
                .map(s -> {
                    s.setStatus(status);
                    return indexSuggestionRepository.save(s);
                })
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + id));
    }

    private Set<String> getExistingIndexes(MonitoredDatabase db) {
        try {
            JdbcTemplate jt = createJdbcTemplate(db);
            String sql = """
                    SELECT tablename, indexdef
                    FROM pg_indexes
                    WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
                    """;
            Set<String> indexes = new HashSet<>();
            jt.queryForList(sql).forEach(row -> {
                String table = (String) row.get("tablename");
                String indexDef = (String) row.get("indexdef");
                // Extract column names from index definition
                Matcher m = Pattern.compile("\\(([^)]+)\\)").matcher(indexDef);
                if (m.find()) {
                    for (String col : m.group(1).split(",")) {
                        indexes.add((table + "." + col.trim()).toLowerCase());
                    }
                }
            });
            return indexes;
        } catch (Exception e) {
            log.warn("Could not retrieve existing indexes for {}: {}", db.getName(), e.getMessage());
            return Set.of();
        }
    }

    private List<String> extractTables(String query) {
        List<String> tables = new ArrayList<>();
        Matcher m = TABLE_PATTERN.matcher(query);
        while (m.find()) {
            tables.add(m.group(1));
        }
        return tables;
    }

    private List<String> extractWhereColumns(String query) {
        List<String> columns = new ArrayList<>();
        Matcher m = WHERE_COLUMN_PATTERN.matcher(query);
        while (m.find()) {
            columns.add(m.group(1));
        }
        return columns;
    }

    private double estimateImprovement(SlowQuery sq) {
        // Heuristic: higher shared_blks_read ratio = more disk I/O = more benefit from index
        if (sq.getSharedBlksHit() + sq.getSharedBlksRead() == 0) return 30.0;
        double ioRatio = (double) sq.getSharedBlksRead() / (sq.getSharedBlksHit() + sq.getSharedBlksRead());
        return Math.min(90.0, 20.0 + ioRatio * 70.0);
    }

    private JdbcTemplate createJdbcTemplate(MonitoredDatabase db) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(db.getJdbcUrl());
        ds.setUsername(db.getUsername());
        ds.setPassword(db.getEncryptedPassword());
        return new JdbcTemplate(ds);
    }
}
