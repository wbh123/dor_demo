package com.wust.dormitory.notification;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificationTemplateService {
    public static final Set<String> VARIABLE_WHITELIST = Set.of(
            "studentName", "studentNumber", "batchName", "buildingName",
            "roomNumber", "bedCode", "openAt", "closeAt", "actionUrl");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]+)}}");

    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureAccessService featureAccessService;

    public NotificationTemplateService(
            NamedParameterJdbcTemplate jdbc,
            FeatureAccessService featureAccessService) {
        this.jdbc = jdbc;
        this.featureAccessService = featureAccessService;
    }

    public List<Map<String, Object>> list() {
        featureAccessService.require(FeatureCodes.P3_NOTIFICATION_TEMPLATE_VIEW);
        return jdbc.queryForList("""
                SELECT template.id, template.template_code, template.template_name,
                       template.built_in AS builtIn, template.enabled,
                       revision.id AS revision_id, revision.revision,
                       revision.title_zh_cn, revision.content_zh_cn,
                       revision.title_en_us, revision.content_en_us,
                       revision.creation_reason AS creationReason,
                       revision.created_by, revision.created_at
                FROM notification_template template
                JOIN notification_template_revision revision
                  ON revision.id=template.current_revision_id
                ORDER BY template.built_in DESC, template.template_code
                """, Map.of());
    }

    @Transactional
    public Map<String, Object> createRevision(
            Long templateId,
            TemplateCommand command,
            CurrentUser operator) {
        featureAccessService.require(FeatureCodes.P3_NOTIFICATION_TEMPLATE_MANAGE);
        TemplateCommand normalized = command.normalized();
        validate(normalized);
        long resolvedTemplateId = templateId == null
                ? createTemplate(normalized, operator)
                : templateId;
        Integer latest = jdbc.queryForObject("""
                SELECT COALESCE(MAX(revision),0)
                FROM notification_template_revision
                WHERE template_id=:templateId
                """, Map.of("templateId", resolvedTemplateId), Integer.class);
        int revision = (latest == null ? 0 : latest) + 1;
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO notification_template_revision
                (template_id, revision, title_zh_cn, content_zh_cn,
                 title_en_us, content_en_us, variables_json,
                 creation_reason, created_by)
                VALUES
                (:templateId,:revision,:titleZhCn,:contentZhCn,
                 :titleEnUs,:contentEnUs,JSON_ARRAY(:variables),
                 :creationReason,:createdBy)
                """, new MapSqlParameterSource()
                .addValue("templateId", resolvedTemplateId)
                .addValue("revision", revision)
                .addValue("titleZhCn", normalized.titleZhCn())
                .addValue("contentZhCn", normalized.contentZhCn())
                .addValue("titleEnUs", normalized.titleEnUs())
                .addValue("contentEnUs", normalized.contentEnUs())
                .addValue("variables", String.join(",", variables(normalized)))
                .addValue("creationReason", normalized.creationReason())
                .addValue("createdBy", operator.userId()),
                keys,
                new String[]{"id"});
        Number revisionId = keys.getKey();
        if (revisionId == null) {
            throw new IllegalStateException("通知模板修订创建成功但未返回编号");
        }
        jdbc.update("""
                UPDATE notification_template
                SET current_revision_id=:revisionId,
                    template_name=:templateName,
                    enabled=:enabled,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:templateId
                """, new MapSqlParameterSource()
                .addValue("revisionId", revisionId.longValue())
                .addValue("templateName", normalized.templateName())
                .addValue("enabled", normalized.enabled() ? 1 : 0)
                .addValue("templateId", resolvedTemplateId));
        return Map.of(
                "templateId", resolvedTemplateId,
                "revisionId", revisionId.longValue(),
                "revision", revision,
                "builtIn", false,
                "enabled", normalized.enabled(),
                "locales", List.of("zh-CN", "en-US"));
    }

    public String render(String text, Map<String, ?> values) {
        if (text == null) return "";
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = requireAllowedVariable(matcher.group(1));
            Object replacement = values == null ? null : values.get(name);
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    replacement == null ? "" : String.valueOf(replacement)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private long createTemplate(TemplateCommand command, CurrentUser operator) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO notification_template
                (template_code, template_name, built_in, enabled, created_by)
                VALUES (:code,:name,0,:enabled,:createdBy)
                """, new MapSqlParameterSource()
                .addValue("code", command.templateCode())
                .addValue("name", command.templateName())
                .addValue("enabled", command.enabled() ? 1 : 0)
                .addValue("createdBy", operator.userId()),
                keys,
                new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("通知模板创建成功但未返回编号");
        return key.longValue();
    }

    private void validate(TemplateCommand command) {
        if (command.templateCode().isBlank() || command.templateName().isBlank()) {
            throw new BusinessException("NOTIFICATION_TEMPLATE_REQUIRED", "模板编号和名称不能为空");
        }
        if (command.creationReason().length() < 2) {
            throw new BusinessException("NOTIFICATION_TEMPLATE_REASON_REQUIRED", "创建修订必须填写原因");
        }
        variables(command);
    }

    private Set<String> variables(TemplateCommand command) {
        Set<String> variables = new LinkedHashSet<>();
        for (String text : List.of(
                command.titleZhCn(), command.contentZhCn(),
                command.titleEnUs(), command.contentEnUs())) {
            Matcher matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) variables.add(requireAllowedVariable(matcher.group(1)));
        }
        return variables;
    }

    private String requireAllowedVariable(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (!VARIABLE_WHITELIST.contains(name)) throw invalidVariable(name);
        return name;
    }

    private BusinessException invalidVariable(String name) {
        return new BusinessException(
                "NOTIFICATION_TEMPLATE_VARIABLE_INVALID",
                "通知模板变量不在白名单中：" + name,
                HttpStatus.BAD_REQUEST);
    }

    public record TemplateCommand(
            String templateCode,
            String templateName,
            String titleZhCn,
            String contentZhCn,
            String titleEnUs,
            String contentEnUs,
            boolean enabled,
            String creationReason) {
        TemplateCommand normalized() {
            return new TemplateCommand(
                    clean(templateCode), clean(templateName), clean(titleZhCn), clean(contentZhCn),
                    clean(titleEnUs), clean(contentEnUs), enabled, clean(creationReason));
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
