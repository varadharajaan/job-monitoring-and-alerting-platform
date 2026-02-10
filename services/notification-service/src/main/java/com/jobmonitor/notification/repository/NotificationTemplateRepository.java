package com.jobmonitor.notification.repository;

import com.jobmonitor.notification.entity.NotificationTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for notification templates — Optional returns for lookups.
 */
@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByIdAndTenantId(UUID id, String tenantId);

    Optional<NotificationTemplate> findByTenantIdAndNameAndChannel(
            String tenantId, String name, String channel);

    Page<NotificationTemplate> findByTenantId(String tenantId, Pageable pageable);

    Page<NotificationTemplate> findByTenantIdAndChannel(String tenantId, String channel,
                                                         Pageable pageable);

    boolean existsByTenantIdAndNameAndChannel(String tenantId, String name, String channel);
}
