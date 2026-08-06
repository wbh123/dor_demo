package com.wust.dormitory.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.persistence.typehandler.JsonNodeTypeHandler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        };
    }
}
