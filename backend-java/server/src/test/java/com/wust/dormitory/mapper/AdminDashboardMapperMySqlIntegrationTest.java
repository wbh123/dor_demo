package com.wust.dormitory.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.AdminDashboardMapper;
import com.wust.dormitory.admin.model.persistence.AdminDashboardStatsRow;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminDashboardMapperMySqlIntegrationTest {
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
                "Docker 不可用，跳过管理工作台 MySQL Mapper 验证");

        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "dashboard_test");
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
    void returnsAllEightDashboardMetricsWithExistingBusinessSemantics() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            AdminDashboardStatsRow stats =
                    session.getMapper(AdminDashboardMapper.class).findStats();

            assertNotNull(stats);
            assertEquals(2L, stats.majorCount());
            assertEquals(4L, stats.studentCount());
            assertEquals(2L, stats.maleStudentCount());
            assertEquals(2L, stats.femaleStudentCount());
            assertEquals(3L, stats.roomCount());
            assertEquals(3L, stats.bedCount());
            assertEquals(4L, stats.activeAssignmentCount());
            assertEquals(3L, stats.openBatchCount());
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
            statement.execute("DROP TABLE IF EXISTS bed_assignment");
            statement.execute("DROP TABLE IF EXISTS selection_batch");
            statement.execute("DROP TABLE IF EXISTS bed");
            statement.execute("DROP TABLE IF EXISTS room");
            statement.execute("DROP TABLE IF EXISTS student");
            statement.execute("DROP TABLE IF EXISTS major");
            statement.execute("""
                    CREATE TABLE major (
                        id BIGINT PRIMARY KEY,
                        enabled TINYINT(1) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE student (
                        id BIGINT PRIMARY KEY,
                        gender VARCHAR(8) NOT NULL
                    )
                    """);
            statement.execute("CREATE TABLE room (id BIGINT PRIMARY KEY)");
            statement.execute("""
                    CREATE TABLE bed (
                        id BIGINT PRIMARY KEY,
                        operational_status VARCHAR(32) NOT NULL
                    )
                    """);
            statement.execute("CREATE TABLE bed_assignment (id BIGINT PRIMARY KEY)");
            statement.execute("""
                    CREATE TABLE selection_batch (
                        id BIGINT PRIMARY KEY,
                        batch_status VARCHAR(32) NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO major VALUES
                    (1, 1), (2, 1), (3, 0)
                    """);
            statement.execute("""
                    INSERT INTO student VALUES
                    (1, 'M'), (2, 'M'), (3, 'F'), (4, 'F')
                    """);
            statement.execute("INSERT INTO room VALUES (1), (2), (3)");
            statement.execute("""
                    INSERT INTO bed VALUES
                    (1, 'ENABLED'), (2, 'ENABLED'), (3, 'ENABLED'),
                    (4, 'DISABLED'), (5, 'MAINTENANCE'), (6, 'RETIRED')
                    """);
            statement.execute("INSERT INTO bed_assignment VALUES (1), (2), (3), (4)");
            statement.execute("""
                    INSERT INTO selection_batch VALUES
                    (1, 'PUBLISHED'), (2, 'OPEN'), (3, 'PAUSED'),
                    (4, 'DRAFT'), (5, 'CLOSED'), (6, 'FINISHED')
                    """);
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://"
                + mysql.getHost()
                + ":"
                + mysql.getMappedPort(3306)
                + "/dashboard_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
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
