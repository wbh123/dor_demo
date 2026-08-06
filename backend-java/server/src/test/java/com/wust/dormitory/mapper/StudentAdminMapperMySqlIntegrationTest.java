package com.wust.dormitory.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.StudentAdminMapper;
import com.wust.dormitory.admin.model.persistence.StudentCatalogRow;
import com.wust.dormitory.admin.model.query.StudentCatalogQuery;
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

class StudentAdminMapperMySqlIntegrationTest {
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
                "Docker 不可用，跳过学生查询 MySQL Mapper 验证");

        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "student_query_test");
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
    void filtersCountAndPageStudentsAgainstMySql84() throws Exception {
        String jdbcUrl = jdbcUrl();
        awaitDatabase(jdbcUrl);
        createFixture(jdbcUrl);

        try (SqlSession session = sessionFactory(jdbcUrl).openSession()) {
            StudentAdminMapper mapper = session.getMapper(StudentAdminMapper.class);

            StudentCatalogQuery nameAndGender = new StudentCatalogQuery(
                    "%张%",
                    "F",
                    null,
                    10,
                    0);
            assertEquals(2L, mapper.countStudents(nameAndGender));
            List<StudentCatalogRow> namedStudents = mapper.findStudents(nameAndGender);
            assertEquals(List.of("20260001", "20260003"), namedStudents.stream()
                    .map(StudentCatalogRow::studentNumber)
                    .toList());
            assertEquals("ACTIVE", namedStudents.getFirst().accountStatus());
            assertNull(namedStudents.get(1).accountStatus());

            StudentCatalogQuery secondStudentInMajor = new StudentCatalogQuery(
                    null,
                    null,
                    1L,
                    1,
                    1);
            assertEquals(2L, mapper.countStudents(secondStudentInMajor));
            List<StudentCatalogRow> page = mapper.findStudents(secondStudentInMajor);
            assertEquals(1, page.size());
            assertEquals("20260002", page.getFirst().studentNumber());
            assertEquals("PENDING", page.getFirst().accountStatus());
            assertEquals("CS", page.getFirst().majorCode());
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

    private static void createFixture(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl,
                "root",
                "root-test-password");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS app_user");
            statement.execute("DROP TABLE IF EXISTS student");
            statement.execute("DROP TABLE IF EXISTS major");
            statement.execute("""
                    CREATE TABLE major (
                        id BIGINT PRIMARY KEY,
                        major_code VARCHAR(32) NOT NULL,
                        major_name VARCHAR(128) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE student (
                        id BIGINT PRIMARY KEY,
                        student_number VARCHAR(32) NOT NULL,
                        student_name VARCHAR(128) NOT NULL,
                        gender VARCHAR(8) NOT NULL,
                        major_id BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE app_user (
                        id BIGINT PRIMARY KEY,
                        student_id BIGINT NULL,
                        account_status VARCHAR(32) NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO major VALUES
                    (1, 'CS', '计算机科学与技术'),
                    (2, 'SE', '软件工程')
                    """);
            statement.execute("""
                    INSERT INTO student VALUES
                    (1, '20260001', '张小雨', 'F', 1),
                    (2, '20260002', '李明', 'M', 1),
                    (3, '20260003', '张晓', 'F', 2)
                    """);
            statement.execute("""
                    INSERT INTO app_user VALUES
                    (11, 1, 'ACTIVE'),
                    (12, 2, 'PENDING')
                    """);
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://"
                + mysql.getHost()
                + ":"
                + mysql.getMappedPort(3306)
                + "/student_query_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
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
