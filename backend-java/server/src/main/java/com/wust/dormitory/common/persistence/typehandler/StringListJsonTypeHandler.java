package com.wust.dormitory.common.persistence.typehandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public final class StringListJsonTypeHandler extends AbstractJacksonJsonTypeHandler<List<String>> {
    public StringListJsonTypeHandler(ObjectMapper objectMapper) {
        super(
                objectMapper,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }
}
