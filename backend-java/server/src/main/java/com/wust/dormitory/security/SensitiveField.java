package com.wust.dormitory.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface SensitiveField {
    Category value();

    enum Category {
        PHONE,
        IDENTITY,
        PREFERENCE,
        ADDRESS,
        NETWORK_ADDRESS,
        FREE_TEXT
    }
}
