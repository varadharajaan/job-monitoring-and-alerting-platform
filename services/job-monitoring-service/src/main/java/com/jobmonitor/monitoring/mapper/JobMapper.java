package com.jobmonitor.monitoring.mapper;

import com.jobmonitor.monitoring.dto.ExecutionRequest;
import com.jobmonitor.monitoring.dto.ExecutionResponse;
import com.jobmonitor.monitoring.dto.JobRequest;
import com.jobmonitor.monitoring.dto.JobResponse;
import com.jobmonitor.monitoring.entity.Job;
import com.jobmonitor.monitoring.entity.JobExecution;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for Job and JobExecution entity/DTO conversions.
 * Spring component model — injected via constructor.
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        builder = @org.mapstruct.Builder(disableBuilder = true))
public interface JobMapper {

    JobResponse toResponse(Job job);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "executions", ignore = true)
    Job toEntity(JobRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "executions", ignore = true)
    void updateEntity(JobRequest request, @MappingTarget Job job);

    @Mapping(target = "jobId", source = "job.id")
    ExecutionResponse toExecutionResponse(JobExecution execution);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "job", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    JobExecution toExecutionEntity(ExecutionRequest request);
}
