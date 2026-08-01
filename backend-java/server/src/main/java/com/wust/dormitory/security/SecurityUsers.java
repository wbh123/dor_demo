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

    public static CurrentUser requireAdmin() {
        CurrentUser user = current();
        if (!user.isAdmin()) {
            throw new BusinessException("FORBIDDEN", "仅管理员可以执行该操作", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    public static CurrentUser requireStudent() {
        CurrentUser user = current();
        if (!user.isStudent() || user.studentId() == null) {
            throw new BusinessException("FORBIDDEN", "仅学生可以执行该操作", HttpStatus.FORBIDDEN);
        }
        return user;
    }
}
