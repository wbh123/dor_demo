package com.wust.dormitory.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class RuntimeErrorRecorder {
    private static final Logger log = LoggerFactory.getLogger(RuntimeErrorRecorder.class);
    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)");
    private static final Pattern LONG_NUMERIC_SEGMENT = Pattern.compile("/\\d{8,}(?=/|$)");
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "Authorization", "Cookie", "password", "Secret", "token", "phoneNumber", "studentName", "studentNumber");

    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RuntimeErrorRecorder(
            ObjectMapper objectMapper,
            @Value("${wust.debug.runtime-error-log-enabled:false}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public synchronized void record(
            HttpServletRequest request,
            String requestId,
            int status,
            String errorCode,
            Exception exception) {
        if (!enabled) return;
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", OffsetDateTime.now().toString());
        record.put("requestId", requestId);
        record.put("method", request.getMethod());
        record.put("path", safePath(request));
        record.put("status", status);
        record.put("errorCode", errorCode);
        record.put("exceptionType", exception.getClass().getName());
        record.put("message", exception.getClass().getSimpleName());
        record.put("resourceIds", safeResourceIds(request));
        record.put("queryKeys", safeQueryKeys(request));
        record.put("actorType", request.getUserPrincipal() == null ? "ANONYMOUS" : "AUTHENTICATED");
        record.put("stackTrace", stackFrames(exception));
        try {
            Path target = runtimeLogPath();
            Files.createDirectories(target.getParent());
            Files.writeString(
                    target,
                    objectMapper.writeValueAsString(record) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ioException) {
            log.warn("Unable to append runtime error record for requestId={}", requestId, ioException);
        }
    }

    private String safePath(HttpServletRequest request) {
        Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (routePattern != null) return String.valueOf(routePattern);
        String raw = request.getRequestURI();
        if (raw == null || raw.isBlank()) return "";
        String withoutUuid = UUID_SEGMENT.matcher(raw).replaceAll("/{token}");
        return LONG_NUMERIC_SEGMENT.matcher(withoutUuid).replaceAll("/{id}");
    }

    private Map<String, Long> safeResourceIds(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) return Map.of();
        Map<String, Long> result = new LinkedHashMap<>();
        variables.forEach((rawName, rawValue) -> {
            String name = String.valueOf(rawName);
            if (!name.endsWith("Id") || isSensitiveName(name) || rawValue == null) return;
            String value = String.valueOf(rawValue).trim();
            if (!value.matches("\\d{1,18}")) return;
            try {
                result.put(name, Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // Ignore values outside the signed long range.
            }
        });
        return result;
    }

    private List<String> safeQueryKeys(HttpServletRequest request) {
        return request.getParameterMap().keySet().stream()
                .filter(name -> !isSensitiveName(name))
                .sorted()
                .toList();
    }

    private boolean isSensitiveName(String name) {
        return SENSITIVE_NAMES.stream().anyMatch(sensitive -> sensitive.equalsIgnoreCase(name));
    }

    private List<String> stackFrames(Exception exception) {
        return List.of(exception.getStackTrace()).stream()
                .limit(80)
                .map(StackTraceElement::toString)
                .toList();
    }

    private Path runtimeLogPath() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int depth = 0; depth < 8 && current != null; depth++, current = current.getParent()) {
            if (Files.isDirectory(current.resolve("backend-java")) && Files.isDirectory(current.resolve("frontend"))) {
                return current.resolve("debug/runtime-errors.ndjson");
            }
        }
        return Path.of(System.getProperty("user.dir"), "debug", "runtime-errors.ndjson")
                .toAbsolutePath().normalize();
    }
}
