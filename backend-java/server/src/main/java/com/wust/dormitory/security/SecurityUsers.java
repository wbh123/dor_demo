package com.wust.dormitory.security;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUsers {
    private SecurityUsers() {
    }

    public static CurrentUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw new BusinessException("UNAUTHORIZED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    public static CurrentUser requireSystemAdmin() {
        CurrentUser user = current();
        if (!user.isSystemAdmin()) {
            throw new BusinessException("PLATFORM_ADMIN_REQUIRED", "仅系统管理员可以执行该操作", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    public static CurrentUser requirePlatformOperation() {
        CurrentUser user = requireSystemAdmin();
        if (user.passwordChangeRequired()) {
            throw new BusinessException(
                    "SYSTEM_ADMIN_PASSWORD_CHANGE_REQUIRED",
                    "首次登录或密码重置后必须先修改密码",
                    HttpStatus.FORBIDDEN
            );
        }
        return user;
    }

    public static CurrentUser requireBusinessUser() {
        CurrentUser user = current();
        if (!user.isBusinessUser()) {
            throw new BusinessException("BUSINESS_USER_REQUIRED", "系统管理员不能访问学校业务", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    public static CurrentUser requireAdmin() {
        CurrentUser user = requireBusinessUser();
        if (!user.isAdmin()) {
            throw new BusinessException("FORBIDDEN", "仅管理员可以执行该操作", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    public static CurrentUser requireStudent() {
        CurrentUser user = requireBusinessUser();
        if (!user.isStudent() || user.studentId() == null) {
            throw new BusinessException("FORBIDDEN", "仅学生可以执行该操作", HttpStatus.FORBIDDEN);
        }
        return user;
    }
}
