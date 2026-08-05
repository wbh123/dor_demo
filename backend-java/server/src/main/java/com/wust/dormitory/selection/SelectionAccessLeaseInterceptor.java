package com.wust.dormitory.selection;

import com.wust.dormitory.security.SecurityUsers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SelectionAccessLeaseInterceptor implements HandlerInterceptor {
    static final String HEADER = "X-Selection-Lease-Token";

    private final SelectionAccessLeaseGuard guard;

    public SelectionAccessLeaseInterceptor(SelectionAccessLeaseGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if (!requiresLease(request.getMethod(), request.getRequestURI())) {
            return true;
        }
        guard.requireActive(
                SecurityUsers.requireStudent(),
                request.getHeader(HEADER));
        return true;
    }

    static boolean requiresLease(String method, String uri) {
        if (!"POST".equalsIgnoreCase(method)
                || !uri.startsWith("/api/v1/student/batches/")) {
            return false;
        }
        return uri.endsWith("/hold")
                || uri.endsWith("/confirm")
                || uri.endsWith("/select");
    }
}
