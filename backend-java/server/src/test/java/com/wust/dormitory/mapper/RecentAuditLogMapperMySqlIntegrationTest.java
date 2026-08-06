package com.wust.dormitory.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.mapper.RecentAuditLogMapper;
import com.wust.dormitory.audit.model.persistence.RecentAuditLogRow;
import com.wust.dormitory.config.MybatisConfig;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecentAuditLogMapperMySqlIntegrationTest {
    private static GenericContainer<?> mysql;

    @BeforeAll
    static void startMysql() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(
                dockerAvailable,
                "Docker 不可用，跳过最近审计记录 MySQL Mapper 验证");

        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "recent_audit_test");
        mysql.withExposedPorts(3306);
        mysql.setWaitStrategy(
                Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        mysql.start();
    }

    @AfterAll
    static void stopMysql() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Test
    void returnsNewestAuditRowsWithLimitAndNullableFields() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            List<RecentAuditLogRow> rows =
                    session.getMapper(RecentAuditLogMapper.class).findRecent(2);

            assertEquals(2, rows.size());
            assertEquals(3L, rows.getFirst().id());
            assertEquals("STUDENT_RESET", rows.getFirst().actionType());
            assertNull(rows.getFirst().resourceId());
            assertNull(rows.getFirst().reason());
            assertEquals(2L, rows.get(1).id());
            assertEquals("ROOM_UPDATE", rows.get(1).actionType());
            assertEquals("88", rows.get(1).resourceId());
            assertEquals("容量调整", rows.get(1).reason());
        }
    }

    private static SqlSessionFactory sessionFactory(String jdbcUrl) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername("root");
        dataSource.setPassword("root-test-password");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        new MybatisConfig()
                .mybatisConfigurationCustomizer(new ObjectMapper())
                .customize(configuration);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/**/*.xml"));

        SqlSessionFactory sessionFactory = factoryBean.getObject();
        assertNotNull(sessionFactory);
        return sessionFactory;
    }

    private static void createFixture(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl,
                "root",
                "root-test-password");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS audit_log");
            statement.execute("""
                    CREATE TABLE audit_log (
                        id BIGINT PRIMARY KEY,
                        request_id VARCHAR(128) NULL,
                        operator_user_id BIGINT NULL,
                        operator_type VARCHAR(32) NOT NULL,
                        action_type VARCHAR(128) NOT NULL,
                        resource_type VARCHAR(64) NOT NULL,
                        resource_id VARCHAR(128) NULL,
                        result_status VARCHAR(32) NOT NULL,
                        reason VARCHAR(512) NULL,
                        occurred_at DATETIME(3) NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO audit_log VALUES
                    (1, 'req-1', 5, 'ADMIN', 'MAJOR_UPDATE', 'MAJOR', '1',
                     'SUCCESS', '名称调整', '2026-08-06 10:00:00.000'),
                    (2, 'req-2', 7, 'ADMIN', 'ROOM_UPDATE', 'ROOM', '88',
                     'SUCCESS', '容量调整', '2026-08-06 11:00:00.000'),
                    (3, NULL, NULL, 'SYSTEM', 'STUDENT_RESET', 'STUDENT', NULL,
                     'FAILED', NULL, '2026-08-06 12:00:00.000')
                    """);
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://"
                + mysql.getHost()
                + ":"
                + mysql.getMappedPort(3306)
                + "/recent_audit_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static void awaitDatabase(String jdbcUrl) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(1).toNanos();
        SQLException latest = null;
        while (System.nanoTime() < deadline) {
            try (Connection ignored = DriverManager.getConnection(
                    jdbcUrl,
                    "root",
                    "root-test-password")) {
                return;
            } catch (SQLException exception) {
                latest = exception;
                Thread.sleep(500L);
            }
        }
        throw new SQLException("MySQL Testcontainer 未在预期时间内就绪", latest);
    }
}
