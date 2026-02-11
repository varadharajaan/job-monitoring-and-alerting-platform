package com.jobmonitor.monitoring.integration;

import com.jobmonitor.monitoring.entity.Job;
import com.jobmonitor.monitoring.repository.JobRepository;
import com.jobmonitor.platform.common.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for JobRepository against a real TimescaleDB container.
 * Extends {@link AbstractIntegrationTest} to use shared Testcontainers.
 */
@SpringBootTest
@ActiveProfiles("test")
class JobRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        // Clean job data
        jobRepository.deleteAll();

        // Ensure a tenant exists (FK constraint)
        tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants (id, name, slug, plan, status) VALUES (?, ?, ?, 'FREE', 'ACTIVE') " +
                        "ON CONFLICT (id) DO NOTHING",
                tenantId, "test-tenant-" + tenantId.toString().substring(0, 8),
                "test-" + tenantId.toString().substring(0, 8)
        );
    }

    @Test
    void saveAndFindById() {
        Job job = Job.builder()
                .name("daily-etl")
                .cronExpression("0 0 2 * * *")
                .scheduleType("CRON")
                .status("ACTIVE")
                .tags(List.of("etl", "data"))
                .metadata(Map.of("env", "prod"))
                .build();
        job.setTenantId(tenantId.toString());

        Job saved = jobRepository.save(job);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();

        Optional<Job> found = jobRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("daily-etl");
        assertThat(found.get().getTags()).containsExactly("etl", "data");
        assertThat(found.get().getMetadata()).containsEntry("env", "prod");
    }

    @Test
    void findByTenantIdAndName() {
        Job job = Job.builder()
                .name("unique-job")
                .scheduleType("MANUAL")
                .status("ACTIVE")
                .build();
        job.setTenantId(tenantId.toString());
        jobRepository.save(job);

        Optional<Job> found = jobRepository.findByTenantIdAndName(tenantId.toString(), "unique-job");
        assertThat(found).isPresent();
        assertThat(found.get().getScheduleType()).isEqualTo("MANUAL");
    }

    @Test
    void findByTenantIdAndStatus() {
        Job active = Job.builder().name("active-job").status("ACTIVE").build();
        active.setTenantId(tenantId.toString());
        Job inactive = Job.builder().name("inactive-job").status("INACTIVE").build();
        inactive.setTenantId(tenantId.toString());
        jobRepository.saveAll(List.of(active, inactive));

        Page<Job> activeJobs = jobRepository.findByTenantIdAndStatus(tenantId.toString(), "ACTIVE", Pageable.unpaged());
        assertThat(activeJobs.getContent()).hasSize(1);
        assertThat(activeJobs.getContent().get(0).getName()).isEqualTo("active-job");
    }

    @Test
    void existsByTenantIdAndName_returnsTrueWhenExists() {
        Job job = Job.builder().name("check-me").status("ACTIVE").build();
        job.setTenantId(tenantId.toString());
        jobRepository.save(job);

        assertThat(jobRepository.existsByTenantIdAndName(tenantId.toString(), "check-me")).isTrue();
        assertThat(jobRepository.existsByTenantIdAndName(tenantId.toString(), "nope")).isFalse();
    }

    @Test
    void jsonbColumns_persistCorrectly() {
        Job job = Job.builder()
                .name("jsonb-test")
                .status("ACTIVE")
                .tags(List.of("tag1", "tag2", "tag3"))
                .metadata(Map.of("key1", "val1", "key2", "val2"))
                .build();
        job.setTenantId(tenantId.toString());

        Job saved = jobRepository.save(job);
        Job reloaded = jobRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getTags()).containsExactlyInAnyOrder("tag1", "tag2", "tag3");
        assertThat(reloaded.getMetadata()).hasSize(2);
    }

    @Test
    void multiTenantIsolation_queriesDoNotCrossTenants() {
        // Create a second tenant
        UUID otherTenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants (id, name, slug, plan, status) VALUES (?, ?, ?, 'FREE', 'ACTIVE')",
                otherTenantId, "other-tenant", "other"
        );

        Job job1 = Job.builder().name("tenant1-job").status("ACTIVE").build();
        job1.setTenantId(tenantId.toString());
        Job job2 = Job.builder().name("tenant2-job").status("ACTIVE").build();
        job2.setTenantId(otherTenantId.toString());
        jobRepository.saveAll(List.of(job1, job2));

        List<Job> tenant1Jobs = jobRepository.findActiveJobsByTenant(tenantId.toString(), "ACTIVE");
        List<Job> tenant2Jobs = jobRepository.findActiveJobsByTenant(otherTenantId.toString(), "ACTIVE");

        assertThat(tenant1Jobs).hasSize(1);
        assertThat(tenant1Jobs.get(0).getName()).isEqualTo("tenant1-job");
        assertThat(tenant2Jobs).hasSize(1);
        assertThat(tenant2Jobs.get(0).getName()).isEqualTo("tenant2-job");
    }

    @Test
    void optimisticLocking_versionIncrements() {
        Job job = Job.builder().name("versioned").status("ACTIVE").build();
        job.setTenantId(tenantId.toString());
        Job saved = jobRepository.save(job);
        Long initialVersion = saved.getVersion();

        saved.setDescription("updated description");
        Job updated = jobRepository.save(saved);

        assertThat(updated.getVersion()).isGreaterThan(initialVersion);
    }
}
