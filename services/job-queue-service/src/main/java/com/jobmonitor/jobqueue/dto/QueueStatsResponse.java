package com.jobmonitor.jobqueue.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Queue statistics response DTO.
 */
@Getter
@Builder
public class QueueStatsResponse {
    private final long pendingCount;
    private final long processingCount;
    private final long completedCount;
    private final long failedCount;
    private final long deadLetterCount;
    private final long cancelledCount;
    private final long totalCount;
    private final double processingRate;
}
