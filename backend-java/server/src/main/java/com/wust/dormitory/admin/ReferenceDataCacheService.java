package com.wust.dormitory.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.model.persistence.BuildingStaticRow;
import com.wust.dormitory.admin.model.persistence.FloorCatalogRow;
import com.wust.dormitory.admin.model.persistence.MajorCatalogRow;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReferenceDataCacheService {
    private static final String MAJOR_KEY_PREFIX = "dorm:catalog:majors:";
    private static final String MAJOR_ALL_KEY = MAJOR_KEY_PREFIX + "all";
    private static final String MAJOR_ENABLED_KEY = MAJOR_KEY_PREFIX + "enabled";
    private static final String MAJOR_DISABLED_KEY = MAJOR_KEY_PREFIX + "disabled";
    private static final String BUILDING_KEY_PREFIX = "dorm:building:";
    private static final String BUILDING_STATIC_SUFFIX = ":static";
    private static final String BUILDING_FLOORS_SUFFIX = ":floors";
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AdminCatalogMapper adminCatalogMapper;

    public ReferenceDataCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AdminCatalogMapper adminCatalogMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.adminCatalogMapper = adminCatalogMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            List<Map<String, Object>> all = databaseMajors(null);
            writeList(MAJOR_ALL_KEY, all);
            writeList(MAJOR_ENABLED_KEY, filterEnabled(all, true));
            writeList(MAJOR_DISABLED_KEY, filterEnabled(all, false));
        } catch (RuntimeException ignored) {
            // Redis 或启动期数据库暂不可用时保持失败开放，业务请求仍可直接回源 MySQL。
        }
        try {
            List<BuildingStaticRow> buildings = adminCatalogMapper.findAllBuildingStatic();
            Map<Long, List<Map<String, Object>>> floorsByBuilding = adminCatalogMapper.findAllFloors().stream()
                    .map(FloorCatalogRow::asResponseMap)
                    .collect(Collectors.groupingBy(
                            row -> ((Number) row.get("building_id")).longValue()));
            buildings.forEach(building -> {
                writeMap(buildingStaticKey(building.id()), building.asResponseMap());
                writeList(buildingFloorsKey(building.id()),
                        floorsByBuilding.getOrDefault(building.id(), List.of()));
            });
        } catch (RuntimeException ignored) {
            // 静态目录预热失败不阻断应用启动，后续请求按 key 回源 MySQL。
        }
    }

    public List<Map<String, Object>> majors(Boolean enabled) {
        String key = majorKey(enabled);
        List<Map<String, Object>> cached = readList(key);
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> rows = databaseMajors(enabled);
        writeList(key, rows);
        return rows;
    }

    public Map<String, Object> buildingStatic(long buildingId) {
        String key = buildingStaticKey(buildingId);
        Map<String, Object> cached = readMap(key);
        if (cached != null) {
            return cached;
        }
        BuildingStaticRow row = adminCatalogMapper.findBuildingStatic(buildingId);
        if (row == null) {
            return Map.of();
        }
        Map<String, Object> result = row.asResponseMap();
        writeMap(key, result);
        return result;
    }

    public List<Map<String, Object>> buildingFloors(long buildingId) {
        String key = buildingFloorsKey(buildingId);
        List<Map<String, Object>> cached = readList(key);
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> rows = adminCatalogMapper.findFloors(buildingId).stream()
                .map(FloorCatalogRow::asResponseMap)
                .toList();
        writeList(key, rows);
        return rows;
    }

    public void invalidateMajors() {
        afterCommitOrNow(this::deleteMajorKeys);
    }

    public void invalidateBuilding(long buildingId) {
        afterCommitOrNow(() -> deleteKeys(List.of(
                buildingStaticKey(buildingId),
                buildingFloorsKey(buildingId))));
    }

    private List<Map<String, Object>> databaseMajors(Boolean enabled) {
        return adminCatalogMapper.findMajors(enabled).stream()
                .map(MajorCatalogRow::asResponseMap)
                .toList();
    }

    private List<Map<String, Object>> readList(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null || value.isBlank() ? null : objectMapper.readValue(value, MAP_LIST);
        } catch (JsonProcessingException | RuntimeException ignored) {
            return null;
        }
    }

    private Map<String, Object> readMap(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null || value.isBlank() ? null : objectMapper.readValue(value, MAP);
        } catch (JsonProcessingException | RuntimeException ignored) {
            return null;
        }
    }

    private void writeList(String key, List<Map<String, Object>> rows) {
        writeJson(key, rows);
    }

    private void writeMap(String key, Map<String, Object> row) {
        writeJson(key, row);
    }

    private void writeJson(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException | RuntimeException ignored) {
            // 缓存不可用或序列化失败时不影响 MySQL 查询结果。
        }
    }

    private void deleteMajorKeys() {
        deleteKeys(List.of(MAJOR_ALL_KEY, MAJOR_ENABLED_KEY, MAJOR_DISABLED_KEY));
    }

    private void deleteKeys(List<String> keys) {
        try {
            redisTemplate.delete(keys);
        } catch (RuntimeException ignored) {
            // 删除缓存失败不影响数据库事务结果，后续启动预热或读穿可重建。
        }
    }

    private void afterCommitOrNow(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private String majorKey(Boolean enabled) {
        if (enabled == null) return MAJOR_ALL_KEY;
        return enabled ? MAJOR_ENABLED_KEY : MAJOR_DISABLED_KEY;
    }

    private String buildingStaticKey(long buildingId) {
        return BUILDING_KEY_PREFIX + buildingId + BUILDING_STATIC_SUFFIX;
    }

    private String buildingFloorsKey(long buildingId) {
        return BUILDING_KEY_PREFIX + buildingId + BUILDING_FLOORS_SUFFIX;
    }

    private List<Map<String, Object>> filterEnabled(
            List<Map<String, Object>> rows,
            boolean enabled) {
        return rows.stream()
                .filter(row -> Boolean.valueOf(enabled).equals(row.get("enabled")))
                .toList();
    }
}
