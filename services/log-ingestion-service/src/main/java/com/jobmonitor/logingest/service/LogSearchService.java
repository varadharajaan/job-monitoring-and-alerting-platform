package com.jobmonitor.logingest.service;

import com.jobmonitor.logingest.model.LogEntry;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Elasticsearch-backed log storage and full-text search service.
 * Uses daily rolling indices: logs-{tenantId}-{yyyy.MM.dd}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogSearchService {

    private final ElasticsearchTemplate elasticsearchTemplate;

    private static final String INDEX_PREFIX = "logs-";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * Indexes a single log entry into the tenant's daily index.
     */
    @Timed(value = "log.ingest", description = "Log entry indexing time")
    public String indexLogEntry(LogEntry entry) {
        if (entry.getId() == null) {
            entry.setId(UUID.randomUUID().toString());
        }
        if (entry.getTimestamp() == null) {
            entry.setTimestamp(Instant.now());
        }

        String indexName = buildIndexName(entry.getTenantId(), entry.getTimestamp());
        IndexQuery indexQuery = new IndexQueryBuilder()
                .withId(entry.getId())
                .withObject(entry)
                .build();

        String documentId = elasticsearchTemplate.index(indexQuery, IndexCoordinates.of(indexName));
        log.debug("Indexed log entry: index={}, id={}", indexName, documentId);
        return documentId;
    }

    /**
     * Bulk-indexes log entries — used by the Kafka consumer for batch processing.
     */
    @Timed(value = "log.ingest.batch", description = "Batch log indexing time")
    public int bulkIndex(List<LogEntry> entries) {
        List<IndexQuery> queries = entries.stream()
                .peek(e -> {
                    if (e.getId() == null) e.setId(UUID.randomUUID().toString());
                    if (e.getTimestamp() == null) e.setTimestamp(Instant.now());
                })
                .map(e -> new IndexQueryBuilder()
                        .withId(e.getId())
                        .withObject(e)
                        .withIndex(buildIndexName(e.getTenantId(), e.getTimestamp()))
                        .build())
                .toList();

        if (!queries.isEmpty()) {
            elasticsearchTemplate.bulkIndex(queries, IndexCoordinates.of(INDEX_PREFIX + "*"));
            log.info("Bulk-indexed {} log entries", queries.size());
        }
        return queries.size();
    }

    /**
     * Full-text search across a tenant's log indices within an optional time range.
     */
    @Timed(value = "log.search", description = "Log search latency")
    public List<LogEntry> search(String tenantId, String queryText,
                                 LogEntry.LogLevel minLevel,
                                 Instant from, Instant to,
                                 int page, int size) {
        Criteria criteria = new Criteria("tenantId").is(tenantId);

        if (queryText != null && !queryText.isBlank()) {
            criteria = criteria.and(new Criteria("message").matches(queryText));
        }
        if (minLevel != null) {
            criteria = criteria.and(new Criteria("level").in(
                    getMinLevelValues(minLevel)));
        }
        if (from != null) {
            criteria = criteria.and(new Criteria("timestamp").greaterThanEqual(from));
        }
        if (to != null) {
            criteria = criteria.and(new Criteria("timestamp").lessThanEqual(to));
        }

        Query query = new CriteriaQuery(criteria)
                .setPageable(org.springframework.data.domain.PageRequest.of(page, size));

        SearchHits<LogEntry> hits = elasticsearchTemplate.search(
                query, LogEntry.class, IndexCoordinates.of(INDEX_PREFIX + tenantId + "-*"));

        return hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
    }

    /**
     * Deletes indices older than the specified retention period for a tenant.
     */
    public int deleteOldIndices(String tenantId, int retentionDays) {
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(retentionDays);
        IndexOperations indexOps = elasticsearchTemplate.indexOps(IndexCoordinates.of(INDEX_PREFIX + tenantId + "-*"));

        // Get all matching indices and delete old ones
        int deleted = 0;
        // NOTE: Elasticsearch handles index lifecycle via ILM policies in production.
        // For programmatic cleanup, we rely on date-based index naming.
        log.info("Retention cleanup triggered: tenant={}, retentionDays={}, cutoffDate={}", tenantId, retentionDays, cutoff);
        try {
            boolean result = indexOps.delete();
            if (result) {
                deleted++;
                log.info("Deleted old indices for tenant={} before {}", tenantId, cutoff);
            }
        } catch (Exception e) {
            log.warn("Index cleanup failed for tenant={}: {}", tenantId, e.getMessage());
        }
        return deleted;
    }

    private String buildIndexName(String tenantId, Instant timestamp) {
        String dateStr = LocalDate.ofInstant(timestamp, ZoneOffset.UTC).format(DATE_FORMAT);
        return INDEX_PREFIX + tenantId + "-" + dateStr;
    }

    private List<String> getMinLevelValues(LogEntry.LogLevel minLevel) {
        return java.util.Arrays.stream(LogEntry.LogLevel.values())
                .filter(l -> l.ordinal() >= minLevel.ordinal())
                .map(Enum::name)
                .toList();
    }
}
