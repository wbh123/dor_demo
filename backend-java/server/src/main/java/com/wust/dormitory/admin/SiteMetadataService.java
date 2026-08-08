package com.wust.dormitory.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.SiteMetadataMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SiteMetadataService {
    private static final String BRANDING_KEY = "SITE_BRANDING";
    private static final String LOGIN_CONTENT_KEY = "LOGIN_LEFT_CONTENT";
    private static final String LOGIN_ADMIN_EDITABLE_KEY = "LOGIN_LEFT_ADMIN_EDITABLE";
    private static final String SITE_THEME = "SITE_THEME";
    private static final Set<String> LOGIN_HTML_ALLOWED_TAGS = Set.of(
            "h1", "h2", "h3", "h4",
            "p", "ul", "ol", "li",
            "strong", "em", "b", "i",
            "br", "span", "div", "small", "blockquote", "hr");
    private static final Pattern LOGIN_HTML_TAG_PATTERN = Pattern.compile(
            "(?is)<\\s*(/?)\\s*([a-z][a-z0-9]*)\\s*([^>]*)>");

    private final SiteMetadataMapper mapper;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final String defaultSchoolName;

    public SiteMetadataService(
            SiteMetadataMapper mapper,
            ObjectMapper objectMapper,
            AuditService auditService,
            @Value("${WUST_DORMITORY_INSTITUTION_NAME:示例大学}") String defaultSchoolName) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.defaultSchoolName = clean(defaultSchoolName).isEmpty() ? "示例大学" : clean(defaultSchoolName);
    }

    public Map<String, Object> publicConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("branding", branding());
        result.put("login", loginContent());
        result.put("theme", theme());
        return result;
    }

    public Map<String, Object> platformConfig() {
        Map<String, Object> result = new LinkedHashMap<>(publicConfig());
        result.put("schoolAdminEditable", schoolAdminEditable());
        return result;
    }

    public Map<String, Object> adminLoginConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("login", loginContent());
        result.put("editable", schoolAdminEditable());
        result.put("theme", theme());
        return result;
    }

    @Transactional
    public Map<String, Object> updatePlatform(
            PlatformSiteCommand command,
            CurrentUser operator) {
        Map<String, Object> before = platformConfig();
        Map<String, String> branding = validateBranding(command.branding());
        Map<String, String> login = validateLogin(command.login());
        mapper.upsert(BRANDING_KEY, json(branding), operator.userId());
        mapper.upsert(LOGIN_CONTENT_KEY, json(login), operator.userId());
        mapper.upsert(LOGIN_ADMIN_EDITABLE_KEY,
                Boolean.toString(command.schoolAdminEditable()), operator.userId());
        Map<String, Object> after = platformConfig();
        auditService.success(
                operator,
                "SITE_METADATA_UPDATE",
                "SYSTEM_SETTING",
                null,
                "系统管理员修改学校元数据与登录页展示",
                before,
                after);
        return after;
    }

    @Transactional
    public Map<String, Object> updateLoginForSchoolAdmin(
            LoginContentCommand command,
            CurrentUser operator) {
        if (!schoolAdminEditable()) {
            throw new BusinessException(
                    "LOGIN_PAGE_CUSTOMIZE_FORBIDDEN",
                    "系统管理员尚未授权学校管理员修改登录页左侧内容");
        }
        Map<String, Object> before = adminLoginConfig();
        mapper.upsert(LOGIN_CONTENT_KEY, json(validateLogin(command)), operator.userId());
        Map<String, Object> after = adminLoginConfig();
        auditService.success(
                operator,
                "LOGIN_PAGE_CONTENT_UPDATE",
                "SYSTEM_SETTING",
                null,
                "学校管理员修改登录页左侧展示内容",
                before,
                after);
        return after;
    }

    @Transactional
    public Map<String, Object> updateThemeForSchoolAdmin(
            String requestedTheme,
            CurrentUser operator) {
        String before = theme();
        String after = validateTheme(requestedTheme);
        mapper.upsert(SITE_THEME, after, operator.userId());
        auditService.success(
                operator,
                "SITE_THEME_UPDATE",
                "SYSTEM_SETTING",
                null,
                "学校管理员修改学校主题",
                Map.of("theme", before),
                Map.of("theme", after));
        return Map.of("theme", after);
    }

    private Map<String, Object> branding() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("schoolName", defaultSchoolName);
        defaults.put("squareLogoUrl", "/assets/logo-only.png");
        defaults.put("horizontalLogoUrl", "/assets/logo-title-right.png");
        return readObject(BRANDING_KEY, defaults);
    }

    private Map<String, Object> loginContent() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("html", "<h1>宿舍智能选择系统</h1><p>查看开放批次、完善个人偏好，并在开放时段完成寝室选择。</p>");
        defaults.put("imageUrl", "");
        Map<String, Object> configured = readObject(LOGIN_CONTENT_KEY, defaults);
        Object htmlValue = configured.get("html");
        try {
            validateSafeLoginHtml(htmlValue instanceof String ? (String) htmlValue : "");
            return configured;
        } catch (BusinessException ignored) {
            return new LinkedHashMap<>(defaults);
        }
    }

    private boolean schoolAdminEditable() {
        return Boolean.parseBoolean(String.valueOf(mapper.findValue(LOGIN_ADMIN_EDITABLE_KEY)));
    }

    private String theme() {
        return "green".equals(clean(mapper.findValue(SITE_THEME))) ? "green" : "blue";
    }

    private Map<String, Object> readObject(String key, Map<String, String> defaults) {
        String value = mapper.findValue(key);
        if (value == null || value.isBlank()) return new LinkedHashMap<>(defaults);
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    value,
                    new TypeReference<Map<String, Object>>() { });
            Map<String, Object> result = new LinkedHashMap<>(defaults);
            result.putAll(parsed);
            return result;
        } catch (JsonProcessingException ignored) {
            return new LinkedHashMap<>(defaults);
        }
    }

    private Map<String, String> validateBranding(BrandingCommand command) {
        if (command == null || clean(command.schoolName()).isEmpty()) {
            throw new BusinessException("SCHOOL_NAME_REQUIRED", "学校名称不能为空");
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("schoolName", bounded(command.schoolName(), 128, "学校名称最多128个字符"));
        result.put("squareLogoUrl", bounded(command.squareLogoUrl(), 500, "正方形校徽地址最多500个字符"));
        result.put("horizontalLogoUrl", bounded(command.horizontalLogoUrl(), 500, "长条形校徽地址最多500个字符"));
        return result;
    }

    private Map<String, String> validateLogin(LoginContentCommand command) {
        if (command == null) throw new BusinessException("LOGIN_CONTENT_REQUIRED", "请填写登录页展示内容");
        String html = bounded(command.html(), 8000, "登录页HTML内容最多8000个字符");
        if (html.isEmpty()) throw new BusinessException("LOGIN_CONTENT_REQUIRED", "登录页HTML内容不能为空");
        validateSafeLoginHtml(html);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("html", html);
        result.put("imageUrl", bounded(command.imageUrl(), 500, "登录页图片地址最多500个字符"));
        return result;
    }

    private void validateSafeLoginHtml(String html) {
        Matcher matcher = LOGIN_HTML_TAG_PATTERN.matcher(html);
        int cursor = 0;
        while (matcher.find()) {
            if (html.substring(cursor, matcher.start()).indexOf('<') >= 0) {
                throw unsafeLoginHtml();
            }
            boolean closing = !matcher.group(1).isEmpty();
            String tag = matcher.group(2).toLowerCase(Locale.ROOT);
            String suffix = matcher.group(3).trim();
            if (!LOGIN_HTML_ALLOWED_TAGS.contains(tag)) {
                throw unsafeLoginHtml();
            }
            if (closing ? !suffix.isEmpty() : (!suffix.isEmpty() && !"/".equals(suffix))) {
                throw unsafeLoginHtml();
            }
            cursor = matcher.end();
        }
        if (html.substring(cursor).indexOf('<') >= 0) {
            throw unsafeLoginHtml();
        }
    }

    private BusinessException unsafeLoginHtml() {
        return new BusinessException(
                "LOGIN_CONTENT_UNSAFE",
                "登录页HTML仅支持标题、段落、列表和基础文本格式，不允许脚本、事件属性或可执行嵌入内容");
    }

    private String validateTheme(String value) {
        String normalized = clean(value).toLowerCase(Locale.ROOT);
        if (!"blue".equals(normalized) && !"green".equals(normalized)) {
            throw new BusinessException("SITE_THEME_INVALID", "界面主题仅支持经典蓝或校园绿");
        }
        return normalized;
    }

    private String bounded(String value, int maximum, String message) {
        String normalized = clean(value);
        if (normalized.length() > maximum) throw new BusinessException("SITE_METADATA_TOO_LONG", message);
        return normalized;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("SITE_METADATA_SERIALIZE_FAILED", "站点设置保存失败");
        }
    }

    public record BrandingCommand(
            String schoolName,
            String squareLogoUrl,
            String horizontalLogoUrl) { }

    public record LoginContentCommand(String html, String imageUrl) { }

    public record PlatformSiteCommand(
            BrandingCommand branding,
            LoginContentCommand login,
            boolean schoolAdminEditable) { }
}
