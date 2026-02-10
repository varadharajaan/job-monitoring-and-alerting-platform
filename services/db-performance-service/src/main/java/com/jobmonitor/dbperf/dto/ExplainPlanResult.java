package com.jobmonitor.dbperf.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO wrapping the result of an EXPLAIN ANALYZE or cost estimation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplainPlanResult {
    private String query;
    private String databaseName;
    private String planJson;
    private String costEstimateJson;
    private String errorMessage;
    private boolean success;
    private List<String> rewriteSuggestions;
}
