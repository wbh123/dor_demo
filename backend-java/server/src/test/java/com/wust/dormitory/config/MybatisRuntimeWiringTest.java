package com.wust.dormitory.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MybatisRuntimeWiringTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class))
            .withUserConfiguration(MybatisConfig.class, TestDependencies.class)
            .withPropertyValues(
                    "mybatis-plus.mapper-locations=classpath*:mapper/**/*.xml",
                    "mybatis-plus.configuration.map-underscore-to-camel-case=true");

    @Test
    void createsSqlSessionInfrastructureLoadsXmlAndRegistersMapperBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SqlSessionFactory.class);
            assertThat(context).hasSingleBean(SqlSessionTemplate.class);
            assertThat(context).hasSingleBean(AdminCatalogMapper.class);

            SqlSessionFactory sessionFactory = context.getBean(SqlSessionFactory.class);
            assertThat(sessionFactory.getConfiguration())
                    .isInstanceOf(MybatisConfiguration.class);
            assertThat(sessionFactory.getConfiguration().isMapUnderscoreToCamelCase())
                    .isTrue();
            assertThat(sessionFactory.getConfiguration().hasStatement(
                    "com.wust.dormitory.admin.mapper.AdminCatalogMapper.findMajors"))
                    .isTrue();
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
