package com.jobmonitor.monitoring.entity;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Composite primary key for {@link JobExecution}.
 * TimescaleDB hypertable requires (id, started_at) as PK.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class JobExecutionId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID id;
    private Instant startedAt;
}
