package com.wust.dormitory.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.model.persistence.MajorCatalogRow;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

@Service
public class ReferenceDataCacheService {
    private static final String MAJOR_KEY_PREFIX = "dorm:catalog:majors:";
    private static final String MAJOR_ALL_KEY = MAJOR_KEY_PREFIX + "all";
    private static final String MAJOR_ENABLED_KEY = MAJOR_KEY_PREFIX + "enabled";
    private static final String MAJOR_DISABLED_KEY = MAJOR_KEY_PREFIX + "disabled";
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() { };

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
            write(MAJOR_ALL_KEY, all);
            write(MAJOR_ENABLED_KEY, filterEnabled(all, true));
            write(MAJOR_DISABLED_KEY, filterEnabled(all, false));
        } catch (RuntimeException ignored) {
            // Redis 或启动期数据库暂不可用时保持失败开放，业务请求仍可直接回源 MySQL。
        }
    }

    public List<Map<String, Object>> majors(Boolean enabled) {
        String key = majorKey(enabled);
        List<Map<String, Object>> cached = read(key);
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> rows = databaseMajors(enabled);
        write(key, rows);
        return rows;
    }

    public void invalidateMajors() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteMajorKeys();
                }
            });
            return;
        }
        deleteMajorKeys();
    }

    private List<Map<String, Object>> databaseMajors(Boolean enabled) {
        return adminCatalogMapper.findMajors(enabled).stream()
                .map(MajorCatalogRow::asResponseMap)
                .toList();
    }

    private List<Map<String, Object>> read(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, MAP_LIST);
        } catch (JsonProcessingException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void write(String key, List<Map<String, Object>> rows) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(rows));
        } catch (JsonProcessingException ignored) {
            // 缓存序列化失败不影响 MySQL 查询结果。
        } catch (RuntimeException ignored) {
            // Redis 不可用时保持失败开放。
        }
    }

    private void deleteMajorKeys() {
        try {
            redisTemplate.delete(List.of(MAJOR_ALL_KEY, MAJOR_ENABLED_KEY, MAJOR_DISABLED_KEY));
        } catch (RuntimeException ignored) {
            // 删除缓存失败不影响数据库事务结果，后续启动预热或读穿可重建。
        }
    }

    private String majorKey(Boolean enabled) {
        if (enabled == null) return MAJOR_ALL_KEY;
        return enabled ? MAJOR_ENABLED_KEY : MAJOR_DISABLED_KEY;
    }

    private List<Map<String, Object>> filterEnabled(
            List<Map<String, Object>> rows,
            boolean enabled) {
        return rows.stream()
                .filter(row -> Boolean.valueOf(enabled).equals(row.get("enabled")))
                .toList();
    }
}
