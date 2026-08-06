package com.wust.dormitory.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.BatchCatalogMapper;
import com.wust.dormitory.admin.model.persistence.BatchCatalogRow;
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

class BatchCatalogMapperMySqlIntegrationTest {
    private static GenericContainer<?> mysql;

    @BeforeAll
    static void startMysql() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker 不可用，跳过批次目录 MySQL Mapper 验证");
        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "batch_catalog_test");
        mysql.withExposedPorts(3306);
        mysql.setWaitStrategy(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        mysql.start();
    }

    @AfterAll
    static void stopMysql() {
        if (mysql != null) mysql.stop();
    }

    @Test
    void returnsExplicitFieldsStableOrderAndAggregatedCounts() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            List<BatchCatalogRow> rows = session.getMapper(BatchCatalogMapper.class).findBatches();
            assertEquals(List.of(2L, 1L), rows.stream().map(BatchCatalogRow::id).toList());

            BatchCatalogRow first = rows.getFirst();
            assertEquals("ROOM", first.selectionMode());
            assertEquals(true, first.separateStudentCategories());
            assertEquals(2L, first.eligibleCount());
            assertEquals(2L, first.assignedCount());
            assertEquals(2L, first.bedAssignedCount());
            assertEquals(2L, first.roomAssignedCount());
            assertEquals(1L, first.lockedRoomCount());
            assertEquals(1L, first.unconfirmedBedResidentCount());
            assertNull(first.publishedAt());
            assertNull(first.finishedAt());
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
            statement.execute("DROP TABLE IF EXISTS active_batch_room_lock");
            statement.execute("DROP TABLE IF EXISTS room_assignment");
            statement.execute("DROP TABLE IF EXISTS bed_assignment");
            statement.execute("DROP TABLE IF EXISTS batch_student_eligibility");
            statement.execute("DROP TABLE IF EXISTS selection_batch");
            statement.execute("""
                    CREATE TABLE selection_batch (
                      id BIGINT PRIMARY KEY, batch_code VARCHAR(32), batch_name VARCHAR(128),
                      batch_status VARCHAR(32), selection_mode VARCHAR(16),
                      separate_student_categories TINYINT, questionnaire_version_id BIGINT,
                      matching_weight_scheme_id BIGINT, rule_template_id BIGINT NULL,
                      start_at DATETIME(3), end_at DATETIME(3), hold_duration_seconds INT,
                      hold_renewal_limit SMALLINT, allow_team TINYINT, team_min_size SMALLINT,
                      team_max_size SMALLINT, allow_student_random TINYINT,
                      unselected_strategy VARCHAR(32), rule_version VARCHAR(32), created_by BIGINT,
                      published_at DATETIME(3) NULL, finished_at DATETIME(3) NULL,
                      version INT, created_at DATETIME(3), updated_at DATETIME(3))
                    """);
            statement.execute("CREATE TABLE batch_student_eligibility (id BIGINT PRIMARY KEY, batch_id BIGINT, eligibility_status VARCHAR(32))");
            statement.execute("CREATE TABLE bed_assignment (id BIGINT PRIMARY KEY, batch_id BIGINT)");
            statement.execute("CREATE TABLE room_assignment (id BIGINT PRIMARY KEY, batch_id BIGINT NULL, bed_id BIGINT NULL, assignment_status VARCHAR(16))");
            statement.execute("CREATE TABLE active_batch_room_lock (room_id BIGINT PRIMARY KEY, batch_id BIGINT)");
            statement.execute("""
                    INSERT INTO selection_batch VALUES
                    (1,'B1','较早批次','FINISHED','BED',0,1,1,1,
                     '2026-09-01 08:00:00','2026-09-01 20:00:00',300,1,1,2,5,1,
                     'ADMIN_ALLOCATION','v1',1,'2026-08-01 09:00:00','2026-09-02 09:00:00',1,
                     '2026-08-01 10:00:00','2026-09-02 09:00:00'),
                    (2,'B2','较新批次','DRAFT','ROOM',1,2,2,NULL,
                     '2026-10-01 08:00:00','2026-10-01 20:00:00',600,2,1,2,4,0,
                     'ADMIN_ALLOCATION','v2',1,NULL,NULL,0,
                     '2026-08-01 10:00:00','2026-08-01 10:00:00')
                    """);
            statement.execute("INSERT INTO batch_student_eligibility VALUES (1,2,'ELIGIBLE'),(2,2,'ELIGIBLE'),(3,2,'INELIGIBLE')");
            statement.execute("INSERT INTO bed_assignment VALUES (1,2),(2,2)");
            statement.execute("INSERT INTO room_assignment VALUES (1,2,NULL,'ACTIVE'),(2,2,88,'ACTIVE'),(3,NULL,NULL,'ACTIVE')");
            statement.execute("INSERT INTO active_batch_room_lock VALUES (100,2)");
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                + "/batch_catalog_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
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
