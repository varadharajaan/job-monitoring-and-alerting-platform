package com.jobmonitor.dbperf.repository;

import com.jobmonitor.dbperf.model.IndexSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IndexSuggestionRepository extends JpaRepository<IndexSuggestion, UUID> {

    List<IndexSuggestion> findByDatabaseIdAndStatus(UUID databaseId, IndexSuggestion.SuggestionStatus status);

    List<IndexSuggestion> findByTenantId(String tenantId);
}
