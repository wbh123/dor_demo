package com.wust.dormitory.subscription;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 默认套餐与学校初始开关使用的纯规则。
 *
 * <p>数据库迁移和服务初始化必须复用相同语义：默认套餐只包含程序已经实现的功能，
 * 学校开关只能为系统已经授权、允许学校控制且目录默认启用的功能建立初始记录。</p>
 */
public final class DefaultEntitlementPolicy {
    private DefaultEntitlementPolicy() {
    }

    public static Set<String> defaultPlanFeatures(List<CatalogFeature> catalog) {
        if (catalog == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (CatalogFeature feature : catalog) {
            if (feature != null && feature.enabledInProgram()) {
                result.add(feature.featureCode());
            }
        }
        return Set.copyOf(result);
    }

    public static Map<String, Boolean> initialSchoolSettings(
            List<CatalogFeature> catalog,
            Set<String> systemGranted) {
        if (catalog == null || systemGranted == null || systemGranted.isEmpty()) {
            return Map.of();
        }
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (CatalogFeature feature : catalog) {
            if (feature == null
                    || !feature.enabledInProgram()
                    || !feature.schoolControllable()
                    || !feature.schoolDefaultEnabled()
                    || !systemGranted.contains(feature.featureCode())) {
                continue;
            }
            result.put(feature.featureCode(), Boolean.TRUE);
        }
        return Map.copyOf(result);
    }

    public record CatalogFeature(
            String featureCode,
            boolean enabledInProgram,
            boolean schoolControllable,
            boolean schoolDefaultEnabled) {
        public CatalogFeature {
            if (featureCode == null || featureCode.isBlank()) {
                throw new IllegalArgumentException("featureCode is required");
            }
        }
    }
}
