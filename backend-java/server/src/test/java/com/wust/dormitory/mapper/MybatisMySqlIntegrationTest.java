package com.wust.dormitory.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.mapper.RoomCatalogMapper;
import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;
import com.wust.dormitory.admin.model.persistence.MajorCatalogRow;
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
import java.util.Map;

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
    void resultTypeMapDoesNotTreatFirstScalarColumnAsJsonObject() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            Map<String, Object> row = session.getMapper(MybatisSmokeMapper.class).selectMapRow();
            assertEquals(1L, ((Number) row.get("id")).longValue());
            assertEquals("QUEUED", row.get("taskStatus"));
        }
    }

    @Test
    void roomCatalogMapperAggregatesBedsAndOccupancyInOneQuery() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createCatalogFixture(jdbcUrl);

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

    @Test
    void adminCatalogMapperFiltersMajorsAndAggregatesBuildingCapacity() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createCatalogFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            AdminCatalogMapper mapper = session.getMapper(AdminCatalogMapper.class);

            List<MajorCatalogRow> enabledMajors = mapper.findMajors(true);
            assertEquals(1, enabledMajors.size());
            assertEquals("CS", enabledMajors.getFirst().majorCode());
            assertEquals("计算机科学与技术", enabledMajors.getFirst().majorName());

            List<MajorCatalogRow> allMajors = mapper.findMajors(null);
            assertEquals(List.of("CS", "SE"), allMajors.stream()
                    .map(MajorCatalogRow::majorCode)
                    .toList());

            List<BuildingCatalogRow> buildings = mapper.findBuildings();
            assertEquals(1, buildings.size());
            BuildingCatalogRow building = buildings.getFirst();
            assertEquals("F01", building.buildingCode());
            assertEquals("示例校区", building.campusName());
            assertEquals("MIXED", building.educationLevelScope());
            assertEquals(1L, building.roomCount());
            assertEquals(4L, building.bedCount());
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

    private static void createCatalogFixture(String jdbcUrl) throws SQLException {
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
            statement.execute("DROP TABLE IF EXISTS campus");
            statement.execute("DROP TABLE IF EXISTS major");
            statement.execute("""
                    CREATE TABLE campus (
                        id BIGINT PRIMARY KEY,
                        campus_name VARCHAR(128) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE dormitory_building (
                        id BIGINT PRIMARY KEY,
                        campus_id BIGINT NOT NULL,
                        building_code VARCHAR(32) NOT NULL,
                        building_name VARCHAR(128) NOT NULL,
                        gender_restriction VARCHAR(8) NOT NULL,
                        education_level_scope VARCHAR(32) NOT NULL,
                        resident_scope VARCHAR(32) NOT NULL,
                        enabled TINYINT(1) NOT NULL
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
                    CREATE TABLE major (
                        id BIGINT PRIMARY KEY,
                        major_code VARCHAR(32) NOT NULL,
                        major_name VARCHAR(128) NOT NULL,
                        enabled TINYINT(1) NOT NULL,
                        created_at DATETIME(3) NOT NULL,
                        updated_at DATETIME(3) NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO campus VALUES (1, '示例校区')");
            statement.execute("""
                    INSERT INTO dormitory_building VALUES
                    (1, 1, 'F01', '示例一栋', 'F', 'MIXED', 'MIXED', 1)
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
            statement.execute("""
                    INSERT INTO major VALUES
                    (1, 'CS', '计算机科学与技术', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
                    (2, 'SE', '软件工程', 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
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
