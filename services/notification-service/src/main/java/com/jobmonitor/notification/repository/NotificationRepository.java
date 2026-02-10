package com.jobmonitor.notification.repository;

import com.jobmonitor.notification.entity.Notification;
import com.jobmonitor.notification.entity.NotificationId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for notifications hypertable — time-range partitioned queries.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, NotificationId> {

    Page<Notification> findByTenantIdAndCreatedAtBetween(String tenantId,
                                                          Instant from, Instant to,
                                                          Pageable pageable);

    Page<Notification> findByTenantIdAndChannelAndCreatedAtBetween(String tenantId, String channel,
                                                                    Instant from, Instant to,
                                                                    Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.retryCount < n.maxRetries " +
           "ORDER BY n.priority DESC, n.createdAt ASC")
    List<Notification> findPendingNotifications(@Param("status") String status, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.tenantId = :tenantId AND n.channel = :channel " +
           "AND n.createdAt > :since")
    long countByTenantIdAndChannelSince(@Param("tenantId") String tenantId,
                                         @Param("channel") String channel,
                                         @Param("since") Instant since);
}
