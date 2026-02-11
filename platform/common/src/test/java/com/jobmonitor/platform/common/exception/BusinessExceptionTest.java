package com.jobmonitor.platform.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void constructor_withMessage_usesBusinessErrorCode() {
        BusinessException ex = new BusinessException("something went wrong");
        assertThat(ex.getMessage()).isEqualTo("something went wrong");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void constructor_withMessageAndErrorCode_usesErrorCodeStatus() {
        BusinessException ex = new BusinessException("not found", ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void constructor_withCustomStatus_overridesErrorCodeStatus() {
        BusinessException ex = new BusinessException("custom", HttpStatus.I_AM_A_TEAPOT, ErrorCode.BUSINESS_ERROR);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
    }

    @Test
    void constructor_withCause_preservesCause() {
        RuntimeException cause = new RuntimeException("root cause");
        BusinessException ex = new BusinessException("wrapped", cause, HttpStatus.BAD_GATEWAY, ErrorCode.SERVICE_UNAVAILABLE);
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void getErrorCodeValue_returnsEnumName() {
        BusinessException ex = new BusinessException("test", ErrorCode.RATE_LIMIT_EXCEEDED);
        assertThat(ex.getErrorCodeValue()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }
}
