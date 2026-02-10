package com.jobmonitor.alerting.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link AlertHistory} — required for TimescaleDB hypertable.
 */
public class AlertHistoryId implements Serializable {

    private UUID id;
    private Instant triggeredAt;

    public AlertHistoryId() {}

    public AlertHistoryId(UUID id, Instant triggeredAt) {
        this.id = id;
        this.triggeredAt = triggeredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlertHistoryId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(triggeredAt, that.triggeredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, triggeredAt);
    }
}
