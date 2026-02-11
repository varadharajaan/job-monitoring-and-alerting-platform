package com.jobmonitor.platform.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void allErrorCodes_haveHttpStatusAndMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getHttpStatus()).isNotNull();
            assertThat(code.getDefaultMessage()).isNotBlank();
            assertThat(code.code()).isEqualTo(code.name());
        }
    }

    @Test
    void resourceNotFound_is404() {
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void duplicateResource_is409() {
        assertThat(ErrorCode.DUPLICATE_RESOURCE.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rateLimitExceeded_is429() {
        assertThat(ErrorCode.RATE_LIMIT_EXCEEDED.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void internalError_is500() {
        assertThat(ErrorCode.INTERNAL_ERROR.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void unauthorized_is401() {
        assertThat(ErrorCode.UNAUTHORIZED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forbidden_is403() {
        assertThat(ErrorCode.FORBIDDEN.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void validationFailed_is400() {
        assertThat(ErrorCode.VALIDATION_FAILED.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void methodNotAllowed_is405() {
        assertThat(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }
}
