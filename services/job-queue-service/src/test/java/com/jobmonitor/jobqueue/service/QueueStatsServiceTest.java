package com.jobmonitor.jobqueue.service;

import com.jobmonitor.jobqueue.dto.QueueStatsResponse;
import com.jobmonitor.jobqueue.entity.JobQueueItem;
import com.jobmonitor.jobqueue.repository.JobQueueRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueStatsService Unit Tests")
class QueueStatsServiceTest {

    @Mock private JobQueueRepository queueRepository;

    private QueueStatsService statsService;

    private static final String TENANT_ID = "tenant-stats-001";

    @BeforeEach
    void setUp() {
        statsService = new QueueStatsService(queueRepository);
    }

    @Nested
    @DisplayName("getStats()")
    class GetStatsTests {

        @Test
        @DisplayName("should return correct queue statistics")
        void shouldReturnStats() {
            given(queueRepository.countByTenantIdAndStatus(TENANT_ID, JobQueueItem.Status.PENDING)).willReturn(10L);
            given(queueRepository.countByTenantIdAndStatus(TENANT_ID, JobQueueItem.Status.PROCESSING)).willReturn(5L);
            given(queueRepository.countByTenantIdAndStatus(TENANT_ID, JobQueueItem.Status.COMPLETED)).willReturn(80L);
            given(queueRepository.countByTenantIdAndStatus(TENANT_ID, JobQueueItem.Status.FAILED)).willReturn(3L);
            given(queueRepository.countByTenantIdAndStatus(TENANT_ID, JobQueueItem.Status.DEAD_LETTER)).willReturn(1L);
            given(queueRepository.countByTenantIdAndStatus(TENANT_ID, JobQueueItem.Status.CANCELLED)).willReturn(1L);

            var result = statsService.getStats(TENANT_ID);

            assertThat(result.getPendingCount()).isEqualTo(10L);
            assertThat(result.getProcessingCount()).isEqualTo(5L);
            assertThat(result.getCompletedCount()).isEqualTo(80L);
            assertThat(result.getFailedCount()).isEqualTo(3L);
            assertThat(result.getDeadLetterCount()).isEqualTo(1L);
            assertThat(result.getCancelledCount()).isEqualTo(1L);
            assertThat(result.getTotalCount()).isEqualTo(100L);
            assertThat(result.getProcessingRate()).isEqualTo(80.0);
        }

        @Test
        @DisplayName("should return zero processing rate when total is zero")
        void shouldReturnZeroRateWhenEmpty() {
            given(queueRepository.countByTenantIdAndStatus(eq(TENANT_ID), any())).willReturn(0L);

            var result = statsService.getStats(TENANT_ID);

            assertThat(result.getTotalCount()).isEqualTo(0L);
            assertThat(result.getProcessingRate()).isEqualTo(0.0);
        }
    }
}
