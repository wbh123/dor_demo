package com.wust.dormitory.notification;

public enum NotificationChannel {
    IN_APP,
    SMS,
    EMAIL,
    MOBILE_PUSH;

    public boolean implemented() {
        return this == IN_APP;
    }
}
