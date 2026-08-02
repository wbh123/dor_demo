package com.wust.dormitory.security;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record CurrentUser(
        long userId,
        Long studentId,
        String username,
        String displayName,
        String userType,
        boolean passwordChangeRequired
) {
    public CurrentUser(long userId, Long studentId, String username, String displayName, String userType) {
        this(userId, studentId, username, displayName, userType, false);
    }

    @JsonIgnore
    public boolean isSystemAdmin() {
        return "SYSTEM_ADMIN".equals(userType);
    }

    @JsonIgnore
    public boolean isAdmin() {
        return "ADMIN".equals(userType);
    }

    @JsonIgnore
    public boolean isStudent() {
        return "STUDENT".equals(userType);
    }

    @JsonIgnore
    public boolean isBusinessUser() {
        return isAdmin() || isStudent();
    }
}
