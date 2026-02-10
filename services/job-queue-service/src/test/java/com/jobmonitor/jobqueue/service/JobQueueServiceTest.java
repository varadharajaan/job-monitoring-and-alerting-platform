package com.jobmonitor.jobqueue.service;

import com.jobmonitor.jobqueue.dto.EnqueueRequest;
import com.jobmonitor.jobqueue.dto.QueueItemResponse;
import com.jobmonitor.jobqueue.entity.JobQueueItem;
import com.jobmonitor.jobqueue.repository.JobQueueRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.functional.EventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobQueueService Unit Tests")
class JobQueueServiceTest {

    @Mock private JobQueueRepository queueRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private PlatformProperties platformProperties;

    private JobQueueService queueService;

    private static final String TENANT_ID = "tenant-queue-001";
    private static final UUID ITEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var kafkaConfig = new PlatformProperties.KafkaConfig();
        var topics = new PlatformProperties.KafkaConfig.Topics();
        topics.setQueueEvents("job-monitor.queue-events");
        kafkaConfig.setTopics(topics);
        lenient().when(platformProperties.getKafka()).thenReturn(kafkaConfig);

        var jobQueueConfig = new PlatformProperties.JobQueueConfig();
        jobQueueConfig.setLockTimeout(Duration.ofMinutes(5));
        lenient().when(platformProperties.getJobQueue()).thenReturn(jobQueueConfig);

        queueService = new JobQueueService(queueRepository, eventPublisher, platformProperties);
    }

    private JobQueueItem createItem() {
        var item = new JobQueueItem();
        item.setId(ITEM_ID);
        item.setTenantId(TENANT_ID);
        item.setJobType("DATA_PIPELINE");
        item.setPayload(Map.of("source", "s3://bucket/data"));
        item.setPriority(3);
        item.setStatus(JobQueueItem.Status.PENDING);
        item.setAttemptCount(0);
        item.setMaxAttempts(3);
        item.setCreatedAt(Instant.now());
        return item;
    }

    @Nested
    @DisplayName("enqueue()")
    class EnqueueTests {

        @Test
        @DisplayName("should enqueue item and publish event")
        void shouldEnqueueItem() {
            var request = new EnqueueRequest();
            request.setJobType("DATA_PIPELINE");
            request.setPayload(Map.of("source", "s3://bucket/data"));
            request.setPriority(3);
            request.setMaxAttempts(3);

            var saved = createItem();
            given(queueRepository.save(any(JobQueueItem.class))).willReturn(saved);

            var result = queueService.enqueue(TENANT_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.getJobType()).isEqualTo("DATA_PIPELINE");
            assertThat(result.getStatus()).isEqualTo("PENDING");
            assertThat(result.getPriority()).isEqualTo(3);
            then(eventPublisher).should().publish(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("claimNext()")
    class ClaimNextTests {

        @Test
        @DisplayName("should claim next available item")
        void shouldClaimNext() {
            var item = createItem();
            given(queueRepository.findNextAvailableItem(any(Instant.class)))
                    .willReturn(Optional.of(item));
            given(queueRepository.save(any(JobQueueItem.class))).willReturn(item);

            var result = queueService.claimNext("worker-001");

            assertThat(result).isPresent();
            assertThat(result.get().getAssignedWorker()).isEqualTo("worker-001");
            then(eventPublisher).should().publish(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should return empty when no items available")
        void shouldReturnEmptyWhenNoItems() {
            given(queueRepository.findNextAvailableItem(any(Instant.class)))
                    .willReturn(Optional.empty());

            var result = queueService.claimNext("worker-001");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("complete()")
    class CompleteTests {

        @Test
        @DisplayName("should complete item and publish event")
        void shouldCompleteItem() {
            var item = createItem();
            item.setStatus(JobQueueItem.Status.PROCESSING);
            given(queueRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
            given(queueRepository.save(any(JobQueueItem.class))).willReturn(item);

            var result = queueService.complete(ITEM_ID);

            assertThat(result).isNotNull();
            then(queueRepository).should().save(argThat(i ->
                    i.getStatus() == JobQueueItem.Status.COMPLETED && i.getCompletedAt() != null));
            then(eventPublisher).should().publish(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when item not found")
        void shouldThrowWhenNotFound() {
            given(queueRepository.findById(ITEM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> queueService.complete(ITEM_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("fail()")
    class FailTests {

        @Test
        @DisplayName("should move to DEAD_LETTER when max attempts reached")
        void shouldMoveToDeadLetter() {
            var item = createItem();
            item.setAttemptCount(3);
            item.setMaxAttempts(3);
            given(queueRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
            given(queueRepository.save(any(JobQueueItem.class))).willReturn(item);

            queueService.fail(ITEM_ID, "Processing failed");

            then(queueRepository).should().save(argThat(i ->
                    i.getStatus() == JobQueueItem.Status.DEAD_LETTER &&
                    "Processing failed".equals(i.getErrorMessage())));
        }

        @Test
        @DisplayName("should set status to PENDING for retry when attempts remain")
        void shouldRetryWhenAttemptsRemain() {
            var item = createItem();
            item.setAttemptCount(1);
            item.setMaxAttempts(3);
            given(queueRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
            given(queueRepository.save(any(JobQueueItem.class))).willReturn(item);

            queueService.fail(ITEM_ID, "Transient error");

            then(queueRepository).should().save(argThat(i ->
                    i.getStatus() == JobQueueItem.Status.PENDING));
        }
    }

    @Nested
    @DisplayName("cancel()")
    class CancelTests {

        @Test
        @DisplayName("should cancel item and publish event")
        void shouldCancelItem() {
            var item = createItem();
            given(queueRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            queueService.cancel(ITEM_ID);

            then(queueRepository).should().save(argThat(i ->
                    i.getStatus() == JobQueueItem.Status.CANCELLED));
            then(eventPublisher).should().publish(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when item not found")
        void shouldThrowWhenNotFound() {
            given(queueRepository.findById(ITEM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> queueService.cancel(ITEM_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("listByTenant()")
    class ListByTenantTests {

        @Test
        @DisplayName("should return paginated queue items")
        void shouldReturnPaginatedItems() {
            var pageable = PageRequest.of(0, 20);
            var item = createItem();
            var page = new PageImpl<>(List.of(item), pageable, 1);
            given(queueRepository.findByTenantIdOrderByPriorityAscCreatedAtAsc(TENANT_ID, pageable))
                    .willReturn(page);

            var result = queueService.listByTenant(TENANT_ID, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("listByStatus()")
    class ListByStatusTests {

        @Test
        @DisplayName("should filter by status")
        void shouldFilterByStatus() {
            var pageable = PageRequest.of(0, 20);
            var item = createItem();
            var page = new PageImpl<>(List.of(item), pageable, 1);
            given(queueRepository.findByTenantIdAndStatus(TENANT_ID, JobQueueItem.Status.PENDING, pageable))
                    .willReturn(page);

            var result = queueService.listByStatus(TENANT_ID, "PENDING", pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }
}
