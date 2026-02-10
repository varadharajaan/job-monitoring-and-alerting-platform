package com.jobmonitor.logingest.dto;

import com.jobmonitor.logingest.model.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogSearchResponse {
    private List<LogEntry> results;
    private int page;
    private int size;
    private long total;
}
