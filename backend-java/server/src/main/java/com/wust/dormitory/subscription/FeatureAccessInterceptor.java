package com.wust.dormitory.subscription;

import com.wust.dormitory.security.SecurityUsers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FeatureAccessInterceptor implements HandlerInterceptor {
    private static final Pattern BATCH_PATH = Pattern.compile("/batches/(\\d+)");

    private final FeatureRouteCatalog routeCatalog;
    private final FeatureAccessService featureAccessService;
    private final QuotaService quotaService;

    public FeatureAccessInterceptor(FeatureRouteCatalog routeCatalog,
                                    FeatureAccessService featureAccessService,
                                    QuotaService quotaService) {
        this.routeCatalog = routeCatalog;
        this.featureAccessService = featureAccessService;
        this.quotaService = quotaService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/platform/login")) {
            return true;
        }
        if (uri.startsWith("/api/v1/platform/")) {
            if (uri.endsWith("/password") || uri.endsWith("/logout") || uri.endsWith("/me")) {
                SecurityUsers.requireSystemAdmin();
            } else {
                SecurityUsers.requirePlatformOperation();
            }
            return true;
        }
        if (!uri.startsWith("/api/v1/")) {
            return true;
        }
        if (uri.startsWith("/api/v1/auth/") || uri.startsWith("/actuator/")) {
            return true;
        }
        SecurityUsers.requireBusinessUser();
        Optional<FeatureRouteCatalog.RouteRule> rule = routeCatalog.resolve(request.getMethod(), uri);
        if (rule.isPresent()) {
            FeatureRouteCatalog.RouteRule resolved = rule.get();
            featureAccessService.require(resolved.featureCode(), resolved.accessMode(), extractBatchId(request));
            enforceQuota(request, uri);
        }
        return true;
    }

    private Long extractBatchId(HttpServletRequest request) {
        String parameter = request.getParameter("batchId");
        if (parameter != null && parameter.matches("\\d+")) {
            return Long.valueOf(parameter);
        }
        Matcher matcher = BATCH_PATH.matcher(request.getRequestURI());
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private void enforceQuota(HttpServletRequest request, String uri) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return;
        }
        if (uri.equals("/api/v1/admin/students")) {
            quotaService.requireAvailable(QuotaCodes.MAX_STUDENTS, 1);
        } else if (uri.equals("/api/v1/admin/campuses")) {
            quotaService.requireAvailable(QuotaCodes.MAX_CAMPUSES, 1);
        } else if (uri.equals("/api/v1/admin/buildings")) {
            quotaService.requireAvailable(QuotaCodes.MAX_BUILDINGS, 1);
        } else if (uri.equals("/api/v1/admin/rooms")) {
            quotaService.requireAvailable(QuotaCodes.MAX_ROOMS, 1);
        } else if (uri.equals("/api/v1/admin/beds")) {
            quotaService.requireAvailable(QuotaCodes.MAX_BEDS, 1);
        } else if (uri.equals("/api/v1/admin/batches")) {
            quotaService.requireAvailable(QuotaCodes.MAX_BATCHES_PER_YEAR, 1);
        }
    }
}
