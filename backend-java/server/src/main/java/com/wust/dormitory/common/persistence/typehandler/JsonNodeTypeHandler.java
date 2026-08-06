package com.wust.dormitory.common.persistence.typehandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonNodeTypeHandler extends AbstractJacksonJsonTypeHandler<JsonNode> {
    public JsonNodeTypeHandler(ObjectMapper objectMapper) {
        super(objectMapper, objectMapper.getTypeFactory().constructType(JsonNode.class));
    }
}
