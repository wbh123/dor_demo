package com.wust.dormitory.export;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExportTaskMapperMySqlIntegrationTest {
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
                "Docker 不可用，跳过真实 MySQL 导出任务映射验证");

        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "export_task_test");
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
    void queuedJsonTaskMapsToTypedRowAndCanBeClaimed() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            ExportTaskMapper mapper = session.getMapper(ExportTaskMapper.class);

            ExportTaskQueueRow queued = mapper.findNextQueued();
            assertNotNull(queued);
            assertEquals(1L, queued.id());
            assertEquals("AUDIT", queued.taskType());
            assertEquals("{\"scope\": \"all\"}", queued.requestJson());
            assertEquals("download-token", queued.downloadToken());

            assertEquals(1, mapper.claim(queued.id()));
            session.commit();
        }

        assertEquals("RUNNING", taskStatus(jdbcUrl, 1L));

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            assertNull(session.getMapper(ExportTaskMapper.class).findNextQueued());
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
            statement.execute("DROP TABLE IF EXISTS export_task");
            statement.execute("""
                    CREATE TABLE export_task (
                        id BIGINT PRIMARY KEY,
                        task_type VARCHAR(64) NOT NULL,
                        task_status VARCHAR(32) NOT NULL,
                        request_json JSON NOT NULL,
                        download_token VARCHAR(128) NOT NULL,
                        started_at DATETIME(3) NULL,
                        progress INT NOT NULL DEFAULT 0,
                        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                    )
                    """);
            statement.execute("""
                    INSERT INTO export_task
                    (id, task_type, task_status, request_json, download_token)
                    VALUES
                    (1, 'AUDIT', 'QUEUED', JSON_OBJECT('scope', 'all'), 'download-token')
                    """);
        }
    }

    private static String taskStatus(String jdbcUrl, long taskId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl,
                "root",
                "root-test-password");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT task_status FROM export_task WHERE id=" + taskId)) {
            if (!result.next()) {
                return null;
            }
            return result.getString(1);
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                + "/export_task_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static void awaitDatabase(String jdbcUrl) throws InterruptedException {
        SQLException lastFailure = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try (Connection ignored = DriverManager.getConnection(
                    jdbcUrl,
                    "root",
                    "root-test-password")) {
                return;
            } catch (SQLException exception) {
                lastFailure = exception;
                Thread.sleep(1000L);
            }
        }
        throw new IllegalStateException("MySQL 未在预期时间内就绪", lastFailure);
    }
}
