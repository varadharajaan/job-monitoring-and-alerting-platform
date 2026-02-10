package com.jobmonitor.monitoring.service;

import com.jobmonitor.monitoring.dto.*;
import com.jobmonitor.monitoring.entity.Job;
import com.jobmonitor.monitoring.entity.JobExecution;
import com.jobmonitor.monitoring.mapper.JobMapper;
import com.jobmonitor.monitoring.repository.JobExecutionRepository;
import com.jobmonitor.monitoring.repository.JobRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.PlatformEvent;
import com.jobmonitor.platform.common.exception.DuplicateResourceException;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.functional.EventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link JobService} — Mockito + AssertJ + lambda assertions.
 * Validates functional patterns: Optional chains, EntityValidator, event publishing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobService Unit Tests")
class JobServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobExecutionRepository executionRepository;
    @Mock private JobMapper jobMapper;
    @Mock private PlatformProperties properties;
    @Mock private EventPublisher<PlatformEvent> eventPublisher;

    @InjectMocks private JobService jobService;

    private static final String TENANT_ID = "tenant-001";
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final String JOB_NAME = "etl-pipeline";
    private static final String TOPIC = "job-monitor.job-events";

    // ── Test fixtures using lambdas ──

    private final Function<UUID, Job> createJob = id -> {
        var job = Job.builder()
                .name(JOB_NAME)
                .description("ETL pipeline job")
                .cronExpression("0 0 * * *")
                .scheduleType("CRON")
                .slaSeconds(3600)
                .status("ACTIVE")
                .build();
        job.setId(id);
        job.setTenantId(TENANT_ID);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        job.setVersion(0L);
        return job;
    };

    private final Function<String, JobRequest> createRequest = name ->
            JobRequest.builder()
                    .name(name)
                    .description("Test job")
                    .cronExpression("0 */5 * * *")
                    .scheduleType("CRON")
                    .slaSeconds(1800)
                    .build();

    private final Function<UUID, JobResponse> createResponse = id ->
            JobResponse.builder()
                    .id(id)
                    .tenantId(TENANT_ID)
                    .name(JOB_NAME)
                    .status("ACTIVE")
                    .build();

    @BeforeEach
    void setUp() {
        // Configure PlatformProperties mock chain
        var kafkaConfig = new PlatformProperties.KafkaConfig();
        var topics = new PlatformProperties.KafkaConfig.Topics();
        topics.setJobEvents(TOPIC);
        kafkaConfig.setTopics(topics);
        lenient().when(properties.getKafka()).thenReturn(kafkaConfig);
    }

    // ──────────── createJob Tests ────────────

    @Nested
    @DisplayName("createJob()")
    class CreateJobTests {

        @Test
        @DisplayName("should create job and publish event on valid request")
        void shouldCreateJobSuccessfully() {
            // Given
            var request = createRequest.apply(JOB_NAME);
            var entity = createJob.apply(JOB_ID);
            var response = createResponse.apply(JOB_ID);

            given(jobMapper.toEntity(request)).willReturn(entity);
            given(jobRepository.findByTenantIdAndName(TENANT_ID, JOB_NAME)).willReturn(Optional.empty());
            given(jobRepository.save(any(Job.class))).willReturn(entity);
            given(jobMapper.toResponse(entity)).willReturn(response);

            // When
            var result = jobService.createJob(TENANT_ID, request);

            // Then — lambda assertions
            assertThat(result)
                    .isNotNull()
                    .satisfies(r -> {
                        assertThat(r.getId()).isEqualTo(JOB_ID);
                        assertThat(r.getName()).isEqualTo(JOB_NAME);
                        assertThat(r.getStatus()).isEqualTo("ACTIVE");
                    });

            then(jobRepository).should().save(any(Job.class));
            then(eventPublisher).should().publish(eq(TOPIC), eq(TENANT_ID), any(PlatformEvent.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when name exists for tenant")
        void shouldThrowOnDuplicateName() {
            // Given
            var request = createRequest.apply(JOB_NAME);
            var existing = createJob.apply(UUID.randomUUID());
            var newJob = createJob.apply(UUID.randomUUID());

            given(jobMapper.toEntity(request)).willReturn(newJob);
            given(jobRepository.findByTenantIdAndName(TENANT_ID, JOB_NAME))
                    .willReturn(Optional.of(existing));

            // When/Then — lambda exception assertion
            assertThatThrownBy(() -> jobService.createJob(TENANT_ID, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).contains(JOB_NAME));

            then(jobRepository).should(never()).save(any());
        }
    }

    // ──────────── getJob Tests ────────────

    @Nested
    @DisplayName("getJob()")
    class GetJobTests {

        @Test
        @DisplayName("should return job response when found")
        void shouldReturnJob() {
            var entity = createJob.apply(JOB_ID);
            var response = createResponse.apply(JOB_ID);

            given(jobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID)).willReturn(Optional.of(entity));
            given(jobMapper.toResponse(entity)).willReturn(response);

            var result = jobService.getJob(TENANT_ID, JOB_ID);

            assertThat(result.getId()).isEqualTo(JOB_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when job not found")
        void shouldThrowWhenNotFound() {
            given(jobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> jobService.getJob(TENANT_ID, JOB_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ──────────── listJobs Tests ────────────

    @Nested
    @DisplayName("listJobs()")
    class ListJobsTests {

        @Test
        @DisplayName("should filter by status when provided via Optional")
        void shouldFilterByStatus() {
            var pageable = PageRequest.of(0, 20);
            var job = createJob.apply(JOB_ID);
            var page = new PageImpl<>(List.of(job), pageable, 1);
            var response = createResponse.apply(JOB_ID);

            given(jobRepository.findByTenantIdAndStatus(TENANT_ID, "ACTIVE", pageable))
                    .willReturn(page);
            given(jobMapper.toResponse(job)).willReturn(response);

            var result = jobService.listJobs(TENANT_ID, Optional.of("ACTIVE"), pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return all jobs when status Optional is empty")
        void shouldReturnAllWhenNoStatus() {
            var pageable = PageRequest.of(0, 20);
            var page = new PageImpl<>(List.of(createJob.apply(JOB_ID)), pageable, 1);
            var response = createResponse.apply(JOB_ID);

            given(jobRepository.findByTenantId(TENANT_ID, pageable)).willReturn(page);
            given(jobMapper.toResponse(any())).willReturn(response);

            var result = jobService.listJobs(TENANT_ID, Optional.empty(), pageable);

            assertThat(result.getContent()).isNotEmpty();
            then(jobRepository).should(never()).findByTenantIdAndStatus(any(), any(), any());
        }
    }

    // ──────────── deactivateJob Tests ────────────

    @Nested
    @DisplayName("deactivateJob()")
    class DeactivateJobTests {

        @Test
        @DisplayName("should deactivate job and publish event")
        void shouldDeactivateJob() {
            var entity = createJob.apply(JOB_ID);
            given(jobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID)).willReturn(Optional.of(entity));
            given(jobRepository.save(any(Job.class))).willReturn(entity);

            jobService.deactivateJob(TENANT_ID, JOB_ID);

            then(jobRepository).should().save(argThat(job -> "INACTIVE".equals(job.getStatus())));
            then(eventPublisher).should().publish(eq(TOPIC), eq(TENANT_ID), any(PlatformEvent.class));
        }

        @Test
        @DisplayName("should throw when job not found for deactivation")
        void shouldThrowWhenDeactivatingNonExistent() {
            given(jobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> jobService.deactivateJob(TENANT_ID, JOB_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ──────────── recordExecution Tests ────────────

    @Nested
    @DisplayName("recordExecution()")
    class RecordExecutionTests {

        @Test
        @DisplayName("should record execution with Optional nullable fields")
        void shouldRecordExecution() {
            var job = createJob.apply(JOB_ID);
            var request = ExecutionRequest.builder()
                    .status("SUCCESS")
                    .startedAt(Instant.now())
                    .durationMs(5000L)
                    .build();
            var execution = JobExecution.builder()
                    .job(job)
                    .status("SUCCESS")
                    .build();
            execution.setId(UUID.randomUUID());
            execution.setTenantId(TENANT_ID);

            var executionResponse = ExecutionResponse.builder()
                    .id(execution.getId())
                    .status("SUCCESS")
                    .build();

            given(jobRepository.findByIdAndTenantId(JOB_ID, TENANT_ID)).willReturn(Optional.of(job));
            given(jobMapper.toExecutionEntity(request)).willReturn(execution);
            given(executionRepository.save(any())).willReturn(execution);
            given(jobMapper.toExecutionResponse(execution)).willReturn(executionResponse);

            var result = jobService.recordExecution(TENANT_ID, JOB_ID, request);

            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            then(eventPublisher).should().publish(eq(TOPIC), eq(TENANT_ID), any(PlatformEvent.class));
        }
    }
}
