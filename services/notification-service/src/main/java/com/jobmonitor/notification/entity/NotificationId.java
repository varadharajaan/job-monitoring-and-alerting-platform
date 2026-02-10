package com.jobmonitor.notification.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link Notification} — required for TimescaleDB hypertable.
 */
public class NotificationId implements Serializable {

    private UUID id;
    private Instant createdAt;

    public NotificationId() {}

    public NotificationId(UUID id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt);
    }
}
