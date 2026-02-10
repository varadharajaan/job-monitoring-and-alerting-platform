package com.jobmonitor.notification.mapper;

import com.jobmonitor.notification.dto.*;
import com.jobmonitor.notification.entity.Notification;
import com.jobmonitor.notification.entity.NotificationTemplate;
import org.mapstruct.*;

/**
 * MapStruct mapper for notification domain — entity ↔ DTO conversions.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

    TemplateResponse toTemplateResponse(NotificationTemplate template);

    NotificationTemplate toTemplateEntity(TemplateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateTemplateEntity(TemplateRequest request, @MappingTarget NotificationTemplate entity);

    @Mapping(source = "template.id", target = "templateId")
    NotificationResponse toNotificationResponse(Notification notification);

    Notification toNotificationEntity(SendNotificationRequest request);
}
