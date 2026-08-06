package com.wust.dormitory.common.persistence.typehandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class StringMapJsonTypeHandler extends AbstractJacksonJsonTypeHandler<Map<String, String>> {
    public StringMapJsonTypeHandler(ObjectMapper objectMapper) {
        super(
                objectMapper,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
    }
}
