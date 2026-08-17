package com.wust.dormitory.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.config.MybatisConfig;
import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SystemReadinessResourceMapperMySqlIntegrationTest {
    private static GenericContainer<?> mysql;

    @BeforeAll
    static void startMysql() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker 不可用，跳过上线体检资源 Mapper 验证");
        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "readiness_resource_test");
        mysql.withExposedPorts(3306);
        mysql.setWaitStrategy(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        mysql.start();
    }

    @AfterAll
    static void stopMysql() {
        if (mysql != null) mysql.stop();
    }

    @Test
    void maintenanceBedCountsTowardPhysicalCapacityAndRoomModeResidentIsNotLost() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            Map<String, Object> summary = session.getMapper(SystemReadinessMapper.class).resourceSummary();
            assertEquals(1L, number(summary, "validBeds"));
            assertEquals(1L, number(summary, "activeResidents"));
            assertEquals(0L, number(summary, "occupiedBeds"));
            assertEquals(0L, number(summary, "capacityMismatchRooms"));
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
        new MybatisConfig().mybatisConfigurationCustomizer(new ObjectMapper()).customize(configuration);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        assertNotNull(factory);
        return factory;
    }

    private static void createFixture(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "root", "root-test-password");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE campus (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE dormitory_building (id BIGINT PRIMARY KEY, campus_id BIGINT)");
            statement.execute("CREATE TABLE dormitory_floor (id BIGINT PRIMARY KEY, building_id BIGINT)");
            statement.execute("CREATE TABLE room (id BIGINT PRIMARY KEY, floor_id BIGINT, operational_status VARCHAR(32), capacity INT)");
            statement.execute("CREATE TABLE bed (id BIGINT PRIMARY KEY, room_id BIGINT, operational_status VARCHAR(32))");
            statement.execute("CREATE TABLE room_assignment (id BIGINT PRIMARY KEY, student_id BIGINT, bed_id BIGINT NULL, assignment_status VARCHAR(32))");

            statement.execute("INSERT INTO campus VALUES (1)");
            statement.execute("INSERT INTO dormitory_building VALUES (10,1)");
            statement.execute("INSERT INTO dormitory_floor VALUES (20,10)");
            statement.execute("INSERT INTO room VALUES (30,20,'ENABLED',2)");
            statement.execute("INSERT INTO bed VALUES (40,30,'ENABLED'),(41,30,'MAINTENANCE')");
            statement.execute("INSERT INTO room_assignment VALUES (50,100,NULL,'ACTIVE')");
        }
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toLowerCase());
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                + "/readiness_resource_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static void awaitDatabase(String jdbcUrl) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(1).toNanos();
        SQLException latest = null;
        while (System.nanoTime() < deadline) {
            try (Connection ignored = DriverManager.getConnection(jdbcUrl, "root", "root-test-password")) {
                return;
            } catch (SQLException exception) {
                latest = exception;
                Thread.sleep(500L);
            }
        }
        throw new SQLException("MySQL 8.4 Testcontainer 未在预期时间内就绪", latest);
    }
}
