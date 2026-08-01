package com.wust.dormitory.security;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record CurrentUser(
        long userId,
        Long studentId,
        String username,
        String displayName,
        String userType
) {
    @JsonIgnore
    public boolean isAdmin() {
        return "ADMIN".equals(userType);
    }

    @JsonIgnore
    public boolean isStudent() {
        return "STUDENT".equals(userType);
    }
}
