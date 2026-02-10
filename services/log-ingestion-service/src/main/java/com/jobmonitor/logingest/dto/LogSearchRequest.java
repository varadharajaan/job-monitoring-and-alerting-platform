package com.jobmonitor.logingest.dto;

import com.jobmonitor.logingest.model.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogSearchRequest {
    private String tenantId;
    private String query;
    private LogEntry.LogLevel minLevel;
    private Instant from;
    private Instant to;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 50;
}
