package com.jobmonitor.monitoring.service;

import com.jobmonitor.monitoring.dto.*;
import com.jobmonitor.monitoring.entity.Job;
import com.jobmonitor.monitoring.entity.JobExecution;
import com.jobmonitor.monitoring.mapper.JobMapper;
import com.jobmonitor.monitoring.repository.JobExecutionRepository;
import com.jobmonitor.monitoring.repository.JobRepository;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.dto.PageResponse;
import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.platform.common.exception.DuplicateResourceException;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.functional.EntityValidator;
import com.jobmonitor.platform.common.functional.EventPublisher;
import com.jobmonitor.platform.common.event.PlatformEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Job monitoring service with extensive use of lambdas, Optional, and functional interfaces.
 * Every lookup returns Optional; null checks are replaced with Optional chains.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final JobMapper jobMapper;
    private final PlatformProperties properties;
    private final EventPublisher<PlatformEvent> eventPublisher;

    // ──────────── Functional Interfaces (composition patterns) ────────────

    /** Validates that a job name is unique within a tenant — throws on duplicate. */
    private final EntityValidator<Job> uniqueNameValidator = job ->
            jobRepository.findByTenantIdAndName(job.getTenantId(), job.getName())
                    .filter(existing -> !existing.getId().equals(job.getId()))
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException("Job", job.getName());
                    });

    /** Transforms a Job entity into a response DTO — used as Function reference. */
    private final Function<Job, JobResponse> toResponse = jobMapper::toResponse;

    /** Transforms a JobExecution entity into a response DTO. */
    private final Function<JobExecution, ExecutionResponse> toExecutionResponse =
            jobMapper::toExecutionResponse;

    /** Resolves the Kafka topic name from config — no hardcoded string. */
    private final Supplier<String> jobEventsTopic = () ->
            properties.getKafka().getTopics().getJobEvents();

    // ──────────── Job CRUD ────────────

    @Transactional
    public JobResponse createJob(String tenantId, JobRequest request) {
        log.info("Creating job '{}' for tenant={}", request.getName(), tenantId);

        var job = jobMapper.toEntity(request);
        job.setTenantId(tenantId);
        job.setStatus("ACTIVE");

        uniqueNameValidator.validate(job);

        var saved = jobRepository.save(job);

        publishJobEvent(saved, JobEvent.Action.REGISTERED, eventBuilder ->
                eventBuilder.cronExpression(saved.getCronExpression()));

        return toResponse.apply(saved);
    }

    public JobResponse getJob(String tenantId, UUID jobId) {
        return findJobOrThrow(tenantId, jobId)
                .map(toResponse)
                .orElseThrow(notFound("Job", jobId));
    }

    public PageResponse<JobResponse> listJobs(String tenantId, Optional<String> status,
                                               Pageable pageable) {
        var page = status
                .filter(s -> !s.isBlank())
                .map(s -> jobRepository.findByTenantIdAndStatus(tenantId, s, pageable))
                .orElseGet(() -> jobRepository.findByTenantId(tenantId, pageable));

        return PageResponse.of(page.map(toResponse::apply));
    }

    @Transactional
    public JobResponse updateJob(String tenantId, UUID jobId, JobRequest request) {
        return findJobOrThrow(tenantId, jobId)
                .map(updateWith(request))
                .map(jobRepository::save)
                .map(toResponse)
                .orElseThrow(notFound("Job", jobId));
    }

    @Transactional
    public void deactivateJob(String tenantId, UUID jobId) {
        findJobOrThrow(tenantId, jobId)
                .ifPresentOrElse(
                        deactivate(),
                        () -> { throw new ResourceNotFoundException("Job", jobId); }
                );
    }

    // ──────────── Job Executions ────────────

    @Transactional
    public ExecutionResponse recordExecution(String tenantId, UUID jobId,
                                              ExecutionRequest request) {
        var job = findJobOrThrow(tenantId, jobId)
                .orElseThrow(notFound("Job", jobId));

        var execution = jobMapper.toExecutionEntity(request);
        execution.setJob(job);
        execution.setTenantId(tenantId);

        Optional.ofNullable(request.getStartedAt())
                .ifPresent(execution::setStartedAt);

        Optional.ofNullable(request.getDurationMs())
                .filter(ms -> ms > 0)
                .ifPresent(execution::setDurationMs);

        var saved = executionRepository.save(execution);

        var action = resolveAction(saved.getStatus());
        publishJobEvent(job, action, eventBuilder -> eventBuilder
                .executionId(saved.getId())
                .exitCode(saved.getExitCode())
                .errorMessage(saved.getErrorMessage())
                .executionStartTime(saved.getStartedAt())
                .executionEndTime(saved.getCompletedAt()));

        log.info("Recorded execution id={} for job={}, status={}",
                saved.getId(), jobId, saved.getStatus());

        return toExecutionResponse.apply(saved);
    }

    public PageResponse<ExecutionResponse> listExecutions(String tenantId, UUID jobId,
                                                           Pageable pageable) {
        var page = executionRepository.findByJobIdAndTenantId(jobId, tenantId, pageable);
        return PageResponse.of(page.map(toExecutionResponse::apply));
    }

    public long countFailuresSince(UUID jobId, Instant since) {
        return executionRepository.countByJobIdAndStatusSince(jobId, "FAILED", since);
    }

    // ──────────── Private Functional Helpers ────────────

    private Optional<Job> findJobOrThrow(String tenantId, UUID jobId) {
        return jobRepository.findByIdAndTenantId(jobId, tenantId);
    }

    /** Returns a UnaryOperator that applies the update request to the existing job. */
    private UnaryOperator<Job> updateWith(JobRequest request) {
        return existing -> {
            uniqueNameValidator.validate(existing);
            jobMapper.updateEntity(request, existing);
            return existing;
        };
    }

    /** Returns a Consumer that deactivates a job and publishes event. */
    private Consumer<Job> deactivate() {
        return job -> {
            job.setStatus("INACTIVE");
            jobRepository.save(job);
            publishJobEvent(job, JobEvent.Action.COMPLETED, builder -> {});
            log.info("Deactivated job id={}, name={}", job.getId(), job.getName());
        };
    }

    /** Factory for not-found exception suppliers — avoids creating exceptions eagerly. */
    private Supplier<ResourceNotFoundException> notFound(String type, Object id) {
        return () -> new ResourceNotFoundException(type, id);
    }

    /** Maps execution status strings to event actions — functional dispatch. */
    private JobEvent.Action resolveAction(String status) {
        return Optional.ofNullable(status)
                .map(String::toUpperCase)
                .map(s -> {
                    switch (s) {
                        case "RUNNING":  return JobEvent.Action.STARTED;
                        case "SUCCESS":  return JobEvent.Action.COMPLETED;
                        case "FAILED":   return JobEvent.Action.FAILED;
                        case "TIMEOUT":  return JobEvent.Action.SLA_VIOLATED;
                        case "RETRYING": return JobEvent.Action.RETRYING;
                        default:         return JobEvent.Action.STARTED;
                    }
                })
                .orElse(JobEvent.Action.STARTED);
    }

    /** Publishes a job event with a customizable builder function. */
    private void publishJobEvent(Job job, JobEvent.Action action,
                                  Consumer<JobEvent.JobEventBuilder> customizer) {
        var builder = JobEvent.builder()
                .tenantId(job.getTenantId())
                .jobId(job.getId())
                .action(action)
                .jobName(job.getName());

        customizer.accept(builder);

        eventPublisher.publish(jobEventsTopic.get(), job.getTenantId(), builder.build());
    }
}
