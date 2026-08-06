package com.wust.dormitory.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.RoomCatalogMapper;
import com.wust.dormitory.admin.model.persistence.RoomCatalogRow;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisMySqlIntegrationTest {
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
                "Docker 不可用，跳过真实 MySQL Mapper 验证");

        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "mybatis_test");
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
    void mapperXmlExecutesAgainstMySql84() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            assertEquals(1, session.getMapper(MybatisSmokeMapper.class).selectOne());
        }
    }

    @Test
    void roomCatalogMapperAggregatesBedsAndOccupancyInOneQuery() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createRoomCatalogFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            RoomCatalogMapper mapper = session.getMapper(RoomCatalogMapper.class);
            List<RoomCatalogRow> rooms = mapper.findRooms(1L, "F");

            assertEquals(1, rooms.size());
            RoomCatalogRow room = rooms.getFirst();
            assertEquals("F-301", room.roomNumber());
            assertEquals("GRADUATE_ONLY", room.educationLevelScope());
            assertEquals("F", room.buildingGenderRestriction());
            assertEquals("MIXED", room.buildingEducationLevelScope());
            assertEquals("MIXED", room.buildingResidentScope());
            assertEquals(4L, room.bedCount());
            assertEquals(2L, room.enabledBedCount());
            assertEquals(1L, room.disabledBedCount());
            assertEquals(1L, room.maintenanceBedCount());
            assertEquals(3L, room.activeResidentCount());
            assertEquals(2L, room.confirmedBedCount());
            assertEquals(1L, room.unconfirmedBedCount());
            assertEquals(1L, room.remainingCapacity());
            assertTrue(mapper.findRooms(1L, "M").isEmpty());
        }
    }

    private static SqlSessionFactory sessionFactory(String jdbcUrl) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername("root");
        dataSource.setPassword("root-test-password");

        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
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

    private static void createRoomCatalogFixture(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl,
                "root",
                "root-test-password");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS room_assignment");
            statement.execute("DROP TABLE IF EXISTS bed");
            statement.execute("DROP TABLE IF EXISTS room");
            statement.execute("DROP TABLE IF EXISTS dormitory_floor");
            statement.execute("DROP TABLE IF EXISTS dormitory_building");
            statement.execute("""
                    CREATE TABLE dormitory_building (
                        id BIGINT PRIMARY KEY,
                        building_code VARCHAR(32) NOT NULL,
                        building_name VARCHAR(128) NOT NULL,
                        gender_restriction VARCHAR(8) NOT NULL,
                        education_level_scope VARCHAR(32) NOT NULL,
                        resident_scope VARCHAR(32) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE dormitory_floor (
                        id BIGINT PRIMARY KEY,
                        building_id BIGINT NOT NULL,
                        floor_number INT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE room (
                        id BIGINT PRIMARY KEY,
                        floor_id BIGINT NOT NULL,
                        room_number VARCHAR(32) NOT NULL,
                        room_type VARCHAR(32) NOT NULL,
                        capacity INT NOT NULL,
                        gender_restriction VARCHAR(8) NOT NULL,
                        education_level_scope VARCHAR(32) NOT NULL,
                        resident_scope VARCHAR(32) NOT NULL,
                        operational_status VARCHAR(32) NOT NULL,
                        state_version BIGINT NOT NULL,
                        remark VARCHAR(255) NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE bed (
                        id BIGINT PRIMARY KEY,
                        room_id BIGINT NOT NULL,
                        operational_status VARCHAR(32) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE room_assignment (
                        id BIGINT PRIMARY KEY,
                        room_id BIGINT NOT NULL,
                        bed_id BIGINT NULL,
                        assignment_status VARCHAR(32) NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO dormitory_building VALUES
                    (1, 'F01', '北苑一栋', 'F', 'MIXED', 'MIXED')
                    """);
            statement.execute("INSERT INTO dormitory_floor VALUES (10, 1, 3)");
            statement.execute("""
                    INSERT INTO room VALUES
                    (100, 10, 'F-301', 'QUAD', 4, 'F', 'GRADUATE_ONLY',
                     'DOMESTIC_ONLY', 'ENABLED', 9, NULL)
                    """);
            statement.execute("""
                    INSERT INTO bed VALUES
                    (1, 100, 'ENABLED'),
                    (2, 100, 'ENABLED'),
                    (3, 100, 'DISABLED'),
                    (4, 100, 'MAINTENANCE'),
                    (5, 100, 'RETIRED')
                    """);
            statement.execute("""
                    INSERT INTO room_assignment VALUES
                    (1, 100, 1, 'ACTIVE'),
                    (2, 100, 2, 'ACTIVE'),
                    (3, 100, NULL, 'ACTIVE'),
                    (4, 100, 3, 'ENDED')
                    """);
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://"
                + mysql.getHost()
                + ":"
                + mysql.getMappedPort(3306)
                + "/mybatis_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
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
