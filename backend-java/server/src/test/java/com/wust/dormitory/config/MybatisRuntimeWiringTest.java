package com.wust.dormitory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MybatisRuntimeWiringTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MybatisAutoConfiguration.class))
            .withUserConfiguration(MybatisConfig.class, TestDependencies.class);

    @Test
    void createsSqlSessionInfrastructureAndMapperBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SqlSessionFactory.class);
            assertThat(context).hasSingleBean(SqlSessionTemplate.class);
            assertThat(context).hasSingleBean(AdminCatalogMapper.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependencies {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
