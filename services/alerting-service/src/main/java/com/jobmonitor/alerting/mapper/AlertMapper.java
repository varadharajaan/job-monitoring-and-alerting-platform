package com.jobmonitor.alerting.mapper;

import com.jobmonitor.alerting.dto.AlertHistoryResponse;
import com.jobmonitor.alerting.dto.AlertRuleRequest;
import com.jobmonitor.alerting.dto.AlertRuleResponse;
import com.jobmonitor.alerting.entity.AlertHistory;
import com.jobmonitor.alerting.entity.AlertRule;
import org.mapstruct.*;

/**
 * MapStruct mapper for alerting domain — entity ↔ DTO conversions.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AlertMapper {

    AlertRuleResponse toRuleResponse(AlertRule rule);

    AlertRule toRuleEntity(AlertRuleRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateRuleEntity(AlertRuleRequest request, @MappingTarget AlertRule entity);

    @Mapping(source = "alertRule.id", target = "alertRuleId")
    @Mapping(source = "alertRule.name", target = "alertRuleName")
    AlertHistoryResponse toHistoryResponse(AlertHistory history);
}
