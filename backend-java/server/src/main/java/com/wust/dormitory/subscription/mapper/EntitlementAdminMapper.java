package com.wust.dormitory.subscription.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface EntitlementAdminMapper {
    List<Map<String, Object>> findFeatures();

    List<Map<String, Object>> findFeatureOverrides();

    List<Map<String, Object>> findQuotas();

    List<Map<String, Object>> findQuotaOverrides();

    List<Map<String, Object>> findAuditLogs(@Param("limit") int limit);

    List<Map<String, Object>> findFeatureEntitlements(
            @Param("subscriptionId") long subscriptionId,
            @Param("planRevisionId") long planRevisionId,
            @Param("now") LocalDateTime now,
            @Param("includeFuture") boolean includeFuture,
            @Param("featureCode") String featureCode);

    Map<String, Object> lockFeatureDefinition(
            @Param("featureCode") String featureCode,
            @Param("planRevisionId") long planRevisionId);

    List<Map<String, Object>> findActiveFeatureOverridesForUpdate(
            @Param("subscriptionId") long subscriptionId,
            @Param("featureCode") String featureCode,
            @Param("now") LocalDateTime now);

    int closeActiveFeatureOverrides(
            @Param("subscriptionId") long subscriptionId,
            @Param("featureCode") String featureCode,
            @Param("now") LocalDateTime now);

    int insertFeatureOverride(Map<String, Object> override);

    int insertQuotaOverride(Map<String, Object> override);
}
