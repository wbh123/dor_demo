package com.wust.dormitory.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.AdminResidencyAdjustmentMapper;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminResidencyAdjustmentMapperMySqlIntegrationTest {
    private static GenericContainer<?> mysql;

    @BeforeAll
    static void startMysql() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker 不可用，跳过管理员床位调整 MySQL Mapper 验证");
        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "residency_adjustment_test");
        mysql.withExposedPorts(3306);
        mysql.setWaitStrategy(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        mysql.start();
    }

    @AfterAll
    static void stopMysql() {
        if (mysql != null) mysql.stop();
    }

    @Test
    void returnsCompatibleBedsWithBuildingRoomAndOccupancyContext() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            List<Map<String, Object>> beds = session.getMapper(AdminResidencyAdjustmentMapper.class)
                    .findCompatibleBeds(10L, 100L, 1000L);

            assertEquals(2, beds.size());
            Map<String, Object> available = beds.stream()
                    .filter(row -> ((Number) row.get("bed_id")).longValue() == 1001L)
                    .findFirst().orElseThrow();
            assertEquals("AVAILABLE", String.valueOf(available.get("occupancy_source")));
            assertEquals("一号楼 101 · B床", String.valueOf(available.get("display_name")));
            assertTrue(((Number) available.get("selectable")).intValue() == 1);
        }
    }

    private static SqlSessionFactory sessionFactory(String jdbcUrl) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername("root");
        dataSource.setPassword("root-test-password");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
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
            statement.execute("CREATE TABLE student (id BIGINT PRIMARY KEY, student_number VARCHAR(32), student_name VARCHAR(64), gender VARCHAR(8), student_category VARCHAR(32))");
            statement.execute("CREATE TABLE dormitory_building (id BIGINT PRIMARY KEY, building_code VARCHAR(32), building_name VARCHAR(128), enabled TINYINT)");
            statement.execute("CREATE TABLE dormitory_floor (id BIGINT PRIMARY KEY, building_id BIGINT, floor_number INT)");
            statement.execute("CREATE TABLE room (id BIGINT PRIMARY KEY, floor_id BIGINT, room_number VARCHAR(32), capacity INT, resident_scope VARCHAR(32), operational_status VARCHAR(32), gender_restriction VARCHAR(8))");
            statement.execute("CREATE TABLE bed (id BIGINT PRIMARY KEY, room_id BIGINT, bed_code VARCHAR(32), bed_type VARCHAR(32), position_index INT, operational_status VARCHAR(32))");
            statement.execute("CREATE TABLE room_bed_layout (bed_id BIGINT PRIMARY KEY, layout_x DECIMAL(8,3), layout_z DECIMAL(8,3), rotation_degrees INT)");
            statement.execute("CREATE TABLE active_batch_room_lock (room_id BIGINT PRIMARY KEY, batch_id BIGINT)");
            statement.execute("CREATE TABLE room_exchange_participant_lock (student_id BIGINT PRIMARY KEY, exchange_id BIGINT)");
            statement.execute("CREATE TABLE room_assignment (id BIGINT PRIMARY KEY, student_id BIGINT, room_id BIGINT, bed_id BIGINT NULL, assignment_status VARCHAR(32), assigned_at DATETIME(3))");
            statement.execute("CREATE TABLE bed_assignment (id BIGINT PRIMARY KEY, batch_id BIGINT, student_id BIGINT, bed_id BIGINT, assignment_status VARCHAR(32), assigned_at DATETIME(3))");
            statement.execute("CREATE TABLE bed_confirmation_request (id BIGINT PRIMARY KEY, student_id BIGINT, declared_bed_id BIGINT, request_status VARCHAR(32), submitted_at DATETIME(3))");

            statement.execute("INSERT INTO student VALUES (10,'202600000010','甲同学','F','DOMESTIC'),(11,'202600000011','乙同学','F','DOMESTIC')");
            statement.execute("INSERT INTO dormitory_building VALUES (1,'B01','一号楼',1)");
            statement.execute("INSERT INTO dormitory_floor VALUES (1,1,1)");
            statement.execute("INSERT INTO room VALUES (100,1,'101',3,'MIXED','ENABLED','F')");
            statement.execute("INSERT INTO bed VALUES (1000,100,'A床','LOFT_BED_DESK',1,'ENABLED'),(1001,100,'B床','LOFT_BED_DESK',2,'ENABLED'),(1002,100,'C床','SINGLE_BED',3,'ENABLED')");
            statement.execute("INSERT INTO room_bed_layout VALUES (1000,0,0,0),(1001,1,0,0),(1002,2,0,0)");
            statement.execute("INSERT INTO room_assignment VALUES (1,10,100,1000,'ACTIVE','2026-08-01 08:00:00.000')");
            statement.execute("INSERT INTO bed_assignment VALUES (2,1,11,1002,'ACTIVE','2026-08-01 08:00:00.000')");
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                + "/residency_adjustment_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
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
        throw new SQLException("MySQL Testcontainer 未在预期时间内就绪", latest);
    }
}
