package com.wust.dormitory.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisMapperXmlTest {
    @Test
    void mapperXmlNamespaceBindsToAnnotatedInterface() throws Exception {
        String resource = "mapper/test/MybatisSmokeMapper.xml";
        Configuration configuration = new Configuration();

        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(input, "测试 Mapper XML 必须位于 classpath*:mapper/**/*.xml");
            new XMLMapperBuilder(
                    input,
                    configuration,
                    resource,
                    configuration.getSqlFragments())
                    .parse();
        }

        assertTrue(configuration.hasMapper(MybatisSmokeMapper.class));
        assertTrue(configuration.hasStatement(
                "com.wust.dormitory.mapper.MybatisSmokeMapper.selectOne"));
    }
}
