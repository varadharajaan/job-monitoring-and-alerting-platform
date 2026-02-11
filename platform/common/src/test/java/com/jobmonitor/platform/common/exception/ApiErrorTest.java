package com.jobmonitor.platform.common.exception;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

    @Test
    void builder_createsCompleteError() {
        Instant now = Instant.now();
        ApiError error = ApiError.builder()
                .timestamp(now)
                .status(404)
                .error("Not Found")
                .errorCode("RESOURCE_NOT_FOUND")
                .message("Job not found with id: 123")
                .path("/api/v1/jobs/123")
                .traceId("trace-abc")
                .build();

        assertThat(error.getTimestamp()).isEqualTo(now);
        assertThat(error.getStatus()).isEqualTo(404);
        assertThat(error.getError()).isEqualTo("Not Found");
        assertThat(error.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(error.getMessage()).isEqualTo("Job not found with id: 123");
        assertThat(error.getPath()).isEqualTo("/api/v1/jobs/123");
        assertThat(error.getTraceId()).isEqualTo("trace-abc");
    }

    @Test
    void builder_withFieldErrors() {
        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(400)
                .error("Bad Request")
                .errorCode("VALIDATION_FAILED")
                .message("Validation failed")
                .path("/api/v1/jobs")
                .fieldErrors(List.of(
                        ApiError.FieldError.builder()
                                .field("name")
                                .message("must not be blank")
                                .rejectedValue("")
                                .build(),
                        ApiError.FieldError.builder()
                                .field("type")
                                .message("must not be null")
                                .rejectedValue(null)
                                .build()
                ))
                .build();

        assertThat(error.getFieldErrors()).hasSize(2);
        assertThat(error.getFieldErrors().get(0).getField()).isEqualTo("name");
        assertThat(error.getFieldErrors().get(1).getMessage()).isEqualTo("must not be null");
    }

    @Test
    void nullTraceId_isAllowed() {
        ApiError error = ApiError.builder()
                .status(500)
                .error("Internal Server Error")
                .message("unexpected")
                .path("/")
                .build();

        assertThat(error.getTraceId()).isNull();
        assertThat(error.getFieldErrors()).isNull();
    }
}
