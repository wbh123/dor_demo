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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SystemReadinessBatchMapperMySqlIntegrationTest {
    private static GenericContainer<?> mysql;

    @BeforeAll
    static void startMysql() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker 不可用，跳过上线体检批次 Mapper 验证");
        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "readiness_batch_test");
        mysql.withExposedPorts(3306);
        mysql.setWaitStrategy(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        mysql.start();
    }

    @AfterAll
    static void stopMysql() {
        if (mysql != null) mysql.stop();
    }

    @Test
    void pendingParticipantsExcludeOnlyCurrentActiveAssignmentsFromSameBatch() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            SystemReadinessMapper mapper = session.getMapper(SystemReadinessMapper.class);
            assertEquals(3L, mapper.participantCount(42L));
            assertEquals(2L, mapper.pendingParticipantCount(42L));
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
            statement.execute("CREATE TABLE batch_student_eligibility (id BIGINT PRIMARY KEY, batch_id BIGINT, student_id BIGINT, eligibility_status VARCHAR(32))");
            statement.execute("CREATE TABLE room_assignment (id BIGINT PRIMARY KEY, batch_id BIGINT NULL, student_id BIGINT, assignment_status VARCHAR(32))");

            statement.execute("INSERT INTO batch_student_eligibility VALUES (1,42,100,'ELIGIBLE'),(2,42,101,'ELIGIBLE'),(3,42,102,'ELIGIBLE'),(4,42,103,'INELIGIBLE')");
            statement.execute("INSERT INTO room_assignment VALUES (10,42,100,'ACTIVE'),(11,42,101,'ENDED'),(12,42,103,'ACTIVE')");
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                + "/readiness_batch_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
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
