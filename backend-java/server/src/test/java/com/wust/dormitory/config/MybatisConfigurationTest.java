package com.wust.dormitory.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.persistence.typehandler.JsonNodeTypeHandler;
import com.wust.dormitory.common.persistence.typehandler.StringListJsonTypeHandler;
import com.wust.dormitory.common.persistence.typehandler.StringMapJsonTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisConfigurationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void configurationRegistersSupportedJsonTypes() {
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();

        new MybatisConfig()
                .mybatisConfigurationCustomizer(objectMapper)
                .customize(configuration);

        assertInstanceOf(
                JsonNodeTypeHandler.class,
                configuration.getTypeHandlerRegistry().getTypeHandler(JsonNode.class));
        assertInstanceOf(
                StringListJsonTypeHandler.class,
                configuration.getTypeHandlerRegistry().getTypeHandler(List.class));
        assertInstanceOf(
                StringMapJsonTypeHandler.class,
                configuration.getTypeHandlerRegistry().getTypeHandler(Map.class));
    }

    @Test
    void stringListHandlerUsesJsonWithoutLosingOrder() throws Exception {
        StringListJsonTypeHandler handler = new StringListJsonTypeHandler(objectMapper);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        handler.setNonNullParameter(
                statement,
                1,
                List.of("quiet", "early"),
                JdbcType.VARCHAR);
        verify(statement).setString(1, "[\"quiet\",\"early\"]");

        when(resultSet.getString("payload")).thenReturn("[\"quiet\",\"early\"]");
        assertEquals(List.of("quiet", "early"), handler.getNullableResult(resultSet, "payload"));
    }

    @Test
    void stringMapAndJsonNodeHandlersRoundTripDatabaseValues() throws Exception {
        StringMapJsonTypeHandler mapHandler = new StringMapJsonTypeHandler(objectMapper);
        JsonNodeTypeHandler nodeHandler = new JsonNodeTypeHandler(objectMapper);
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.getString("map_payload"))
                .thenReturn("{\"language\":\"zh-CN\",\"theme\":\"dark\"}");
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("language", "zh-CN");
        expected.put("theme", "dark");
        assertEquals(expected, mapHandler.getNullableResult(resultSet, "map_payload"));

        when(resultSet.getString("node_payload"))
                .thenReturn("{\"enabled\":true,\"weight\":3}");
        JsonNode node = nodeHandler.getNullableResult(resultSet, "node_payload");
        assertEquals(true, node.path("enabled").asBoolean());
        assertEquals(3, node.path("weight").asInt());
    }

    @Test
    void blankJsonIsNullAndMalformedJsonIsInfrastructureError() throws Exception {
        JsonNodeTypeHandler handler = new JsonNodeTypeHandler(objectMapper);
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.getString("blank_payload")).thenReturn("   ");
        assertNull(handler.getNullableResult(resultSet, "blank_payload"));

        when(resultSet.getString("broken_payload")).thenReturn("{");
        assertThrows(
                SQLException.class,
                () -> handler.getNullableResult(resultSet, "broken_payload"));
    }
}
