package com.jobmonitor.platform.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceNotFoundExceptionTest {

    @Test
    void constructor_withTypeAndId_formatsMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Job", "uuid-123");
        assertThat(ex.getMessage()).isEqualTo("Job not found with id: uuid-123");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void constructor_withMessage_usesDirectMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("custom not found");
        assertThat(ex.getMessage()).isEqualTo("custom not found");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void isBusinessException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Job", 42);
        assertThat(ex).isInstanceOf(BusinessException.class);
    }
}
