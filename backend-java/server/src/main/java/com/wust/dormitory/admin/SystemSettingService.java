package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemSettingService {
    private static final String STUDENT_WELCOME_MESSAGE = "STUDENT_WELCOME_MESSAGE";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public SystemSettingService(NamedParameterJdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public Map<String, Object> studentWelcome() {
        return one();
    }

    @Transactional
    public Map<String, Object> updateStudentWelcome(
            String message,
            int expectedVersion,
            CurrentUser operator) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isEmpty() || normalized.length() > 1000) {
            throw new BusinessException(
                    "STUDENT_WELCOME_MESSAGE_INVALID",
                    "欢迎语长度必须为1至1000个字符");
        }
        Map<String, Object> before = one();
        int actualVersion = ((Number) before.get("version")).intValue();
        if (actualVersion != expectedVersion) {
            throw new BusinessException(
                    "SYSTEM_SETTING_VERSION_CONFLICT",
                    "欢迎语已经被其他管理员修改，请刷新后重试",
                    HttpStatus.CONFLICT);
        }

        int updated = jdbc.update("""
                UPDATE system_setting
                SET setting_value=:message,
                    updated_by=:updatedBy,
                    version=version+1
                WHERE setting_key=:settingKey
                  AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("message", normalized)
                .addValue("updatedBy", operator.userId())
                .addValue("settingKey", STUDENT_WELCOME_MESSAGE)
                .addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new BusinessException(
                    "SYSTEM_SETTING_VERSION_CONFLICT",
                    "欢迎语已经被其他管理员修改，请刷新后重试",
                    HttpStatus.CONFLICT);
        }

        Map<String, Object> after = one();
        auditService.success(
                operator,
                "SYSTEM_SETTING_UPDATE",
                "SYSTEM_SETTING",
                before.get("id"),
                "更新新生欢迎语",
                before,
                after);
        return after;
    }

    private Map<String, Object> one() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT setting.id,
                       setting.setting_value AS message,
                       setting.version,
                       setting.updated_at,
                       updater.display_name AS updated_by_name
                FROM system_setting setting
                LEFT JOIN app_user updater ON updater.id=setting.updated_by
                WHERE setting.setting_key=:settingKey
                """, Map.of("settingKey", STUDENT_WELCOME_MESSAGE));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "SYSTEM_SETTING_NOT_FOUND",
                    "学生欢迎语配置不存在",
                    HttpStatus.NOT_FOUND);
        }
        return new LinkedHashMap<>(rows.getFirst());
    }
}
