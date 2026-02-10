package com.jobmonitor.gateway.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class IngestionResponse {
    private String eventId;
    private String status;
    private Instant receivedAt;
}
