package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class BatchRuleTemplateService {
    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public BatchRuleTemplateService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT template.id, template.rule_code, template.rule_name,
                       template.revision, template.hold_duration_seconds,
                       template.hold_renewal_limit, template.allow_team,
                       template.team_min_size, template.team_max_size,
                       template.allow_student_random, template.unselected_strategy,
                       template.rule_version, template.enabled, template.is_default,
                       template.version, template.change_reason,
                       template.created_at, template.updated_at,
                       creator.display_name AS created_by_name,
                       (SELECT COUNT(*) FROM selection_batch batch
                        WHERE batch.rule_template_id=template.id) AS batch_count
                FROM batch_rule_template template
                LEFT JOIN app_user creator ON creator.id=template.created_by
                ORDER BY template.rule_code, template.revision DESC
                """, Map.of());
        rows.forEach(this::normalizeBooleans);
        return rows;
    }

    @Transactional
    public Map<String, Object> create(
            CreateCommand command,
            CurrentUser operator) {
        validateCode(command.ruleCode());
        validateCommon(command.asValues());
        requireDefaultToBeEnabled(command.enabled(), command.makeDefault());

        String code = command.ruleCode().trim();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_rule_template WHERE rule_code=:code",
                Map.of("code", code),
                Integer.class);
        if (count != null && count > 0) {
            throw new BusinessException(
                    "BATCH_RULE_TEMPLATE_CODE_CONFLICT",
                    "规则模板编码已经存在",
                    HttpStatus.CONFLICT);
        }

        if (command.makeDefault()) {
            clearDefault();
        }

        long id;
        try {
            id = insert(code, 1, command.asValues(), operator.userId());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "BATCH_RULE_TEMPLATE_CODE_CONFLICT",
                    "规则模板编码已经存在",
                    HttpStatus.CONFLICT);
        }

        Map<String, Object> created = one(id);
        auditService.success(
                operator,
                "BATCH_RULE_TEMPLATE_CREATE",
                "BATCH_RULE_TEMPLATE",
                id,
                command.changeReason().trim(),
                null,
                created);
        return created;
    }

    @Transactional
    public Map<String, Object> createRevision(
            long templateId,
            RevisionCommand command,
            CurrentUser operator) {
        validateCommon(command.asValues());
        requireDefaultToBeEnabled(command.enabled(), command.makeDefault());

        Map<String, Object> source = oneForUpdate(templateId);
        int currentVersion = ((Number) source.get("version")).intValue();
        if (currentVersion != command.expectedVersion()) {
            throw versionConflict();
        }

        int claimed = jdbc.update("""
                UPDATE batch_rule_template
                SET version=version+1
                WHERE id=:id AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", templateId)
                .addValue("expectedVersion", command.expectedVersion()));
        if (claimed != 1) {
            throw versionConflict();
        }

        String code = String.valueOf(source.get("rule_code"));
        Integer latestRevision = jdbc.queryForObject("""
                SELECT revision FROM batch_rule_template
                WHERE rule_code=:code
                ORDER BY revision DESC
                LIMIT 1
                FOR UPDATE
                """, Map.of("code", code), Integer.class);
        int revision = (latestRevision == null ? 0 : latestRevision) + 1;

        if (command.makeDefault()) {
            clearDefault();
        }

        long id;
        try {
            id = insert(code, revision, command.asValues(), operator.userId());
        } catch (DuplicateKeyException exception) {
            throw versionConflict();
        }

        Map<String, Object> created = one(id);
        Map<String, Object> before = new LinkedHashMap<>(source);
        normalizeBooleans(before);
        auditService.success(
                operator,
                "BATCH_RULE_TEMPLATE_REVISE",
                "BATCH_RULE_TEMPLATE",
                id,
                command.changeReason().trim(),
                before,
                created);
        return created;
    }

    public RuleSnapshot resolveForBatch(Long templateId) {
        List<Map<String, Object>> rows;
        if (templateId == null) {
            rows = jdbc.queryForList("""
                    SELECT * FROM batch_rule_template
                    WHERE enabled=1 AND is_default=1
                    ORDER BY id
                    LIMIT 1
                    """, Map.of());
            if (rows.isEmpty()) {
                throw new BusinessException(
                        "BATCH_RULE_TEMPLATE_DEFAULT_REQUIRED",
                        "系统没有可用的默认批次规则模板");
            }
        } else {
            rows = jdbc.queryForList(
                    "SELECT * FROM batch_rule_template WHERE id=:id",
                    Map.of("id", templateId));
            if (rows.isEmpty()) {
                throw new BusinessException(
                        "BATCH_RULE_TEMPLATE_NOT_FOUND",
                        "批次规则模板不存在",
                        HttpStatus.NOT_FOUND);
            }
        }

        Map<String, Object> row = rows.getFirst();
        if (((Number) row.get("enabled")).intValue() != 1) {
            throw new BusinessException(
                    "BATCH_RULE_TEMPLATE_DISABLED",
                    "所选批次规则模板已经停用",
                    HttpStatus.CONFLICT);
        }
        return snapshot(row);
    }

    private long insert(
            String code,
            int revision,
            RuleValues values,
            long operatorId) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO batch_rule_template
                (rule_code, rule_name, revision,
                 hold_duration_seconds, hold_renewal_limit,
                 allow_team, team_min_size, team_max_size,
                 allow_student_random, unselected_strategy, rule_version,
                 enabled, is_default, created_by, change_reason, version)
                VALUES
                (:code, :name, :revision,
                 :holdDurationSeconds, :holdRenewalLimit,
                 :allowTeam, :teamMinSize, :teamMaxSize,
                 :allowStudentRandom, :unselectedStrategy, :ruleVersion,
                 :enabled, :isDefault, :createdBy, :changeReason, 0)
                """, new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("name", values.ruleName().trim())
                .addValue("revision", revision)
                .addValue("holdDurationSeconds", values.holdDurationSeconds())
                .addValue("holdRenewalLimit", values.holdRenewalLimit())
                .addValue("allowTeam", values.allowTeam() ? 1 : 0)
                .addValue("teamMinSize", values.teamMinSize())
                .addValue("teamMaxSize", values.teamMaxSize())
                .addValue("allowStudentRandom", values.allowStudentRandom() ? 1 : 0)
                .addValue("unselectedStrategy", values.unselectedStrategy())
                .addValue("ruleVersion", values.ruleVersion().trim())
                .addValue("enabled", values.enabled() ? 1 : 0)
                .addValue("isDefault", values.makeDefault() ? 1 : 0)
                .addValue("createdBy", operatorId)
                .addValue("changeReason", values.changeReason().trim()),
                keyHolder,
                new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("规则模板创建成功但没有返回主键");
        }
        return key.longValue();
    }

    private void clearDefault() {
        jdbc.update(
                "UPDATE batch_rule_template SET is_default=0 WHERE is_default=1",
                Map.of());
    }

    private void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code.trim()).matches()) {
            throw invalid("模板编码只能包含大写字母、数字、下划线和连字符");
        }
    }

    private void validateCommon(RuleValues values) {
        if (values.ruleName() == null
                || values.ruleName().isBlank()
                || values.ruleName().length() > 128) {
            throw invalid("模板名称长度不正确");
        }
        if (values.holdDurationSeconds() < 30
                || values.holdDurationSeconds() > 3600) {
            throw invalid("临时占用时长必须在30到3600秒之间");
        }
        if (values.holdRenewalLimit() < 0
                || values.holdRenewalLimit() > 20) {
            throw invalid("最大续期次数必须在0到20之间");
        }
        if (values.allowTeam()) {
            if (values.teamMinSize() < 2
                    || values.teamMaxSize() > 5
                    || values.teamMinSize() > values.teamMaxSize()) {
                throw invalid("允许组队时，队伍人数必须在2至5人之间");
            }
        } else if (values.teamMinSize() != 1 || values.teamMaxSize() != 1) {
            throw invalid("不允许组队时，队伍最小和最大人数必须都为1");
        }
        if (!"NONE".equals(values.unselectedStrategy())
                && !"ADMIN_ALLOCATION".equals(values.unselectedStrategy())) {
            throw invalid("未选学生处理策略不受支持");
        }
        if (values.ruleVersion() == null
                || values.ruleVersion().isBlank()
                || values.ruleVersion().length() > 32) {
            throw invalid("规则执行版本长度不正确");
        }
        if (values.changeReason() == null
                || values.changeReason().isBlank()
                || values.changeReason().length() > 500) {
            throw invalid("请填写规则模板创建或修订原因");
        }
    }

    private void requireDefaultToBeEnabled(boolean enabled, boolean makeDefault) {
        if (makeDefault && !enabled) {
            throw invalid("默认模板必须处于启用状态");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException("BATCH_RULE_TEMPLATE_INVALID", message);
    }

    private BusinessException versionConflict() {
        return new BusinessException(
                "BATCH_RULE_TEMPLATE_VERSION_CONFLICT",
                "规则模板已经发生变化，请重新加载后再保存",
                HttpStatus.CONFLICT);
    }

    private Map<String, Object> one(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT template.id, template.rule_code, template.rule_name,
                       template.revision, template.hold_duration_seconds,
                       template.hold_renewal_limit, template.allow_team,
                       template.team_min_size, template.team_max_size,
                       template.allow_student_random, template.unselected_strategy,
                       template.rule_version, template.enabled, template.is_default,
                       template.version, template.change_reason,
                       template.created_at, template.updated_at,
                       creator.display_name AS created_by_name,
                       (SELECT COUNT(*) FROM selection_batch batch
                        WHERE batch.rule_template_id=template.id) AS batch_count
                FROM batch_rule_template template
                LEFT JOIN app_user creator ON creator.id=template.created_by
                WHERE template.id=:id
                """, Map.of("id", id));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "BATCH_RULE_TEMPLATE_NOT_FOUND",
                    "批次规则模板不存在",
                    HttpStatus.NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        normalizeBooleans(result);
        return result;
    }

    private Map<String, Object> oneForUpdate(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM batch_rule_template
                WHERE id=:id
                FOR UPDATE
                """, Map.of("id", id));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "BATCH_RULE_TEMPLATE_NOT_FOUND",
                    "批次规则模板不存在",
                    HttpStatus.NOT_FOUND);
        }
        return new LinkedHashMap<>(rows.getFirst());
    }

    private void normalizeBooleans(Map<String, Object> row) {
        row.put("enabled", numberBoolean(row.get("enabled")));
        row.put("is_default", numberBoolean(row.get("is_default")));
        row.put("allow_team", numberBoolean(row.get("allow_team")));
        row.put("allow_student_random", numberBoolean(row.get("allow_student_random")));
    }

    private boolean numberBoolean(Object value) {
        return value instanceof Number number && number.intValue() == 1;
    }

    private RuleSnapshot snapshot(Map<String, Object> row) {
        return new RuleSnapshot(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("rule_code")),
                ((Number) row.get("revision")).intValue(),
                ((Number) row.get("hold_duration_seconds")).intValue(),
                ((Number) row.get("hold_renewal_limit")).intValue(),
                ((Number) row.get("allow_team")).intValue() == 1,
                ((Number) row.get("team_min_size")).intValue(),
                ((Number) row.get("team_max_size")).intValue(),
                ((Number) row.get("allow_student_random")).intValue() == 1,
                String.valueOf(row.get("unselected_strategy")),
                String.valueOf(row.get("rule_version")));
    }

    public record RuleSnapshot(
            long id,
            String code,
            int revision,
            int holdDurationSeconds,
            int holdRenewalLimit,
            boolean allowTeam,
            int teamMinSize,
            int teamMaxSize,
            boolean allowStudentRandom,
            String unselectedStrategy,
            String ruleVersion) {
    }

    public record RuleValues(
            String ruleName,
            int holdDurationSeconds,
            int holdRenewalLimit,
            boolean allowTeam,
            int teamMinSize,
            int teamMaxSize,
            boolean allowStudentRandom,
            String unselectedStrategy,
            String ruleVersion,
            boolean enabled,
            boolean makeDefault,
            String changeReason) {
    }

    public record CreateCommand(
            String ruleCode,
            String ruleName,
            int holdDurationSeconds,
            int holdRenewalLimit,
            boolean allowTeam,
            int teamMinSize,
            int teamMaxSize,
            boolean allowStudentRandom,
            String unselectedStrategy,
            String ruleVersion,
            boolean enabled,
            boolean makeDefault,
            String changeReason) {
        RuleValues asValues() {
            return new RuleValues(
                    ruleName,
                    holdDurationSeconds,
                    holdRenewalLimit,
                    allowTeam,
                    teamMinSize,
                    teamMaxSize,
                    allowStudentRandom,
                    unselectedStrategy,
                    ruleVersion,
                    enabled,
                    makeDefault,
                    changeReason);
        }
    }

    public record RevisionCommand(
            String ruleName,
            int holdDurationSeconds,
            int holdRenewalLimit,
            boolean allowTeam,
            int teamMinSize,
            int teamMaxSize,
            boolean allowStudentRandom,
            String unselectedStrategy,
            String ruleVersion,
            boolean enabled,
            boolean makeDefault,
            int expectedVersion,
            String changeReason) {
        RuleValues asValues() {
            return new RuleValues(
                    ruleName,
                    holdDurationSeconds,
                    holdRenewalLimit,
                    allowTeam,
                    teamMinSize,
                    teamMaxSize,
                    allowStudentRandom,
                    unselectedStrategy,
                    ruleVersion,
                    enabled,
                    makeDefault,
                    changeReason);
        }
    }
}
