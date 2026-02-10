package com.jobmonitor.dbperf.service;

import com.jobmonitor.dbperf.model.MonitoredDatabase;
import com.jobmonitor.dbperf.model.SlowQuery;
import com.jobmonitor.dbperf.repository.SlowQueryRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monitors slow queries by reading from PostgreSQL's pg_stat_statements
 * extension. Captures queries exceeding a configurable threshold and
 * stores them for historical tracking.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SlowQueryMonitorService {

    private final SlowQueryRepository slowQueryRepository;

    /**
     * Collects current slow queries from a monitored database's pg_stat_statements.
     */
    @Timed(value = "dbperf.slowquery.collect", description = "Slow query collection time")
    public int collectSlowQueries(MonitoredDatabase db) {
        JdbcTemplate jdbcTemplate = createJdbcTemplate(db);
        long thresholdMs = db.getSlowQueryThresholdMs();

        String sql = """
                SELECT query, calls, mean_exec_time, max_exec_time, rows,
                       shared_blks_hit, shared_blks_read
                FROM pg_stat_statements
                WHERE mean_exec_time > ?
                  AND query NOT LIKE '%pg_stat_statements%'
                ORDER BY mean_exec_time DESC
                LIMIT 100
                """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, (double) thresholdMs);
        int count = 0;

        for (Map<String, Object> row : results) {
            String queryText = (String) row.get("query");
            String fingerprint = computeFingerprint(queryText);

            // Check if already captured (dedup by fingerprint)
            List<SlowQuery> existing = slowQueryRepository.findByDatabaseIdAndQueryFingerprint(db.getId(), fingerprint);
            if (!existing.isEmpty()) {
                // Update the existing record with latest stats
                SlowQuery sq = existing.get(0);
                sq.setMeanTimeMs(((Number) row.get("mean_exec_time")).doubleValue());
                sq.setMaxTimeMs(((Number) row.get("max_exec_time")).doubleValue());
                sq.setCalls(((Number) row.get("calls")).longValue());
                sq.setTotalRows(((Number) row.get("rows")).longValue());
                sq.setSharedBlksHit(((Number) row.get("shared_blks_hit")).longValue());
                sq.setSharedBlksRead(((Number) row.get("shared_blks_read")).longValue());
                slowQueryRepository.save(sq);
            } else {
                SlowQuery sq = SlowQuery.builder()
                        .databaseId(db.getId())
                        .tenantId(db.getTenantId())
                        .queryText(queryText)
                        .queryFingerprint(fingerprint)
                        .meanTimeMs(((Number) row.get("mean_exec_time")).doubleValue())
                        .maxTimeMs(((Number) row.get("max_exec_time")).doubleValue())
                        .calls(((Number) row.get("calls")).longValue())
                        .totalRows(((Number) row.get("rows")).longValue())
                        .sharedBlksHit(((Number) row.get("shared_blks_hit")).longValue())
                        .sharedBlksRead(((Number) row.get("shared_blks_read")).longValue())
                        .build();
                slowQueryRepository.save(sq);
                count++;
            }
        }

        log.info("Collected {} new slow queries from database={}, total examined={}",
                count, db.getName(), results.size());
        return count;
    }

    public Page<SlowQuery> getSlowQueriesForDatabase(UUID databaseId, int page, int size) {
        return slowQueryRepository.findByDatabaseIdOrderByMeanTimeMsDesc(databaseId, PageRequest.of(page, size));
    }

    public List<SlowQuery> getSlowQueriesForTenant(String tenantId) {
        return slowQueryRepository.findByTenantIdOrderByMeanTimeMsDesc(tenantId);
    }

    /**
     * Generates a fingerprint (SHA-256 hash) of a normalized query for grouping.
     */
    private String computeFingerprint(String query) {
        String normalized = query.replaceAll("\\s+", " ")
                .replaceAll("'[^']*'", "?")
                .replaceAll("\\b\\d+\\b", "?")
                .trim()
                .toLowerCase();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes());
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return normalized.hashCode() + "";
        }
    }

    private JdbcTemplate createJdbcTemplate(MonitoredDatabase db) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(db.getJdbcUrl());
        ds.setUsername(db.getUsername());
        ds.setPassword(db.getEncryptedPassword());
        return new JdbcTemplate(ds);
    }
}
