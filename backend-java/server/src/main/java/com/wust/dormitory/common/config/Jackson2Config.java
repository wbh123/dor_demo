package com.wust.dormitory.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class Jackson2Config {
    @Bean
    public ObjectMapper jackson2ObjectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}
