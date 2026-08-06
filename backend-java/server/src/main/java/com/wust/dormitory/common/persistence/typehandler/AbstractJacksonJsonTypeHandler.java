package com.wust.dormitory.common.persistence.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

abstract class AbstractJacksonJsonTypeHandler<T> extends BaseTypeHandler<T> {
    private final ObjectMapper objectMapper;
    private final JavaType javaType;

    protected AbstractJacksonJsonTypeHandler(ObjectMapper objectMapper, JavaType javaType) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.javaType = Objects.requireNonNull(javaType, "javaType");
    }

    @Override
    public final void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            T parameter,
            JdbcType jdbcType) throws SQLException {
        try {
            statement.setString(parameterIndex, objectMapper.writeValueAsString(parameter));
        } catch (JsonProcessingException exception) {
            throw new SQLException("无法序列化数据库 JSON 参数", exception);
        }
    }

    @Override
    public final T getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return read(resultSet.getString(columnName));
    }

    @Override
    public final T getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return read(resultSet.getString(columnIndex));
    }

    @Override
    public final T getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return read(statement.getString(columnIndex));
    }

    private T read(String rawValue) throws SQLException {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawValue, javaType);
        } catch (JsonProcessingException exception) {
            throw new SQLException("无法解析数据库 JSON 字段", exception);
        }
    }
}
