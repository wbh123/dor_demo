package com.wust.dormitory.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.persistence.typehandler.JsonNodeTypeHandler;
import com.wust.dormitory.common.persistence.typehandler.StringListJsonTypeHandler;
import com.wust.dormitory.common.persistence.typehandler.StringMapJsonTypeHandler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = "com.wust.dormitory",
        annotationClass = Mapper.class)
public class MybatisConfig {
    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer(ObjectMapper objectMapper) {
        return configuration -> {
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(JsonNode.class, new JsonNodeTypeHandler(objectMapper));
            registry.register(List.class, new StringListJsonTypeHandler(objectMapper));
            registry.register(Map.class, new StringMapJsonTypeHandler(objectMapper));
        };
    }
}
