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

class SystemReadinessStudentMapperMySqlIntegrationTest {
    private static GenericContainer<?> mysql;

    @BeforeAll
    static void startMysql() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker 不可用，跳过上线体检学生 Mapper 验证");
        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "readiness_student_test");
        mysql.withExposedPorts(3306);
        mysql.setWaitStrategy(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        mysql.start();
    }

    @AfterAll
    static void stopMysql() {
        if (mysql != null) mysql.stop();
    }

    @Test
    void studentSummaryCountsOverlappingIssuesPerStudentOnlyOnce() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            SystemReadinessMapper mapper = session.getMapper(SystemReadinessMapper.class);
            Map<String, Object> summary = mapper.studentSummary();

            assertEquals(2L, number(summary, "totalStudents"));
            assertEquals(1L, number(summary, "invalidStudents"));
            assertEquals(1L, number(summary, "missingCriticalFields"));
            assertEquals(1L, number(summary, "missingMajorMapping"));
            assertEquals(1L, number(summary, "invalidDegreeLevel"));
            assertEquals(1L, number(summary, "invalidGender"));
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
            statement.execute("CREATE TABLE major (id BIGINT PRIMARY KEY, enabled TINYINT)");
            statement.execute("CREATE TABLE student (id BIGINT PRIMARY KEY, student_number VARCHAR(32), student_name VARCHAR(64), grade_year INT NULL, major_id BIGINT NULL, degree_level VARCHAR(32) NULL, gender VARCHAR(8) NULL)");
            statement.execute("CREATE TABLE app_user (id BIGINT PRIMARY KEY, student_id BIGINT NULL, account_status VARCHAR(16))");

            statement.execute("INSERT INTO major VALUES (10,1)");
            statement.execute("INSERT INTO student VALUES (100,'202600000001','正常学生',2026,10,'本科','F')");
            statement.execute("INSERT INTO student VALUES (101,'','',NULL,NULL,'UNKNOWN','X')");
            statement.execute("INSERT INTO app_user VALUES (200,100,'ACTIVE'),(201,101,'ACTIVE')");
        }
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toLowerCase());
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                + "/readiness_student_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
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
