package com.wust.dormitory.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.residency.AdminBedSwapService;
import com.wust.dormitory.residency.ResidencyHistoryWriter;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.residency.mapper.ResidencyMapper;
import com.wust.dormitory.roomchange.RoomChangeService;
import com.wust.dormitory.security.AuthTokenService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.selection.BedHoldResetService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminRuntimeMySqlIntegrationTest {
    private static GenericContainer<?> mysql;
    private DriverManagerDataSource dataSource;
    private NamedParameterJdbcTemplate jdbc;
    private CurrentUser operator;

    @BeforeAll
    static void startMysql() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker 不可用，跳过真实 MySQL 管理运行时验证");
        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"));
        mysql.withEnv("MYSQL_ROOT_PASSWORD", "root-test-password");
        mysql.withEnv("MYSQL_DATABASE", "runtime_test");
        mysql.withExposedPorts(3306);
        mysql.setWaitStrategy(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        mysql.start();
    }

    @AfterAll
    static void stopMysql() {
        if (mysql != null) mysql.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        awaitDatabase();
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(jdbcUrl());
        dataSource.setUsername("root");
        dataSource.setPassword("root-test-password");
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        operator = new CurrentUser(1L, null, "admin", "管理员", "ADMIN");
        resetSchema();
    }

    @Test
    void residencyAdjustmentContextExecutesAgainstMySql84() {
        ResidencyService residencyService = mock(ResidencyService.class);
        when(residencyService.current(100L)).thenReturn(Map.of("resident", false));
        AdminResidencyAdjustmentMapper adjustmentMapper = mock(AdminResidencyAdjustmentMapper.class);
        when(adjustmentMapper.findCompatibleBeds(100L, -1L, null)).thenReturn(List.of(Map.of(
                "bed_id", 22L,
                "room_id", 12L,
                "swap_required", 0)));
        AdminStudentResidencyAdjustmentService service =
                new AdminStudentResidencyAdjustmentService(
                        jdbc,
                        residencyService,
                        adjustmentMapper,
                        mock(AdminBedSwapService.class));

        Map<String, Object> result = service.context(100L);

        assertThat(result.get("studentName")).isEqualTo("测试学生");
        assertThat((List<?>) result.get("availableBeds")).hasSize(1);
    }

    @Test
    void confirmBedAndEndResidencyPersistHistoryAgainstMySql84() throws Exception {
        AuditService audit = mock(AuditService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        try (SqlSession sqlSession = residencySqlSession()) {
            ResidencyMapper residencyMapper = sqlSession.getMapper(ResidencyMapper.class);
            ResidencyHistoryWriter historyWriter = new ResidencyHistoryWriter(residencyMapper, objectMapper);
            ResidencyService service = new ResidencyService(
                    residencyMapper, mock(ResidencyPolicyService.class), audit, historyWriter);

            Map<String, Object> confirmed = service.confirmBed(900L, 22L, "现场核查", operator);
            assertThat(((Number) confirmed.get("bed_id")).longValue()).isEqualTo(22L);
            Map<String, Object> ended = service.end(900L, "办理退宿", operator);
            assertThat(ended.get("assignment_status")).isEqualTo("ENDED");
        }
        Integer historyCount = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM room_assignment_history WHERE room_assignment_id=900",
                Integer.class);
        assertThat(historyCount).isEqualTo(2);
    }

    @Test
    void completeResetRunsAgainstCurrentSchemaWithoutActiveResidency() {
        BedHoldResetService holds = mock(BedHoldResetService.class);
        RoomChangeService roomChanges = mock(RoomChangeService.class);
        AuthTokenService tokens = mock(AuthTokenService.class);
        when(holds.releaseAllForStudent(anyLong(), org.mockito.ArgumentMatchers.anyList())).thenReturn(0);
        when(roomChanges.cancelActiveRoomChanges(anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);
        when(tokens.revokeUser(anyLong())).thenReturn(0);
        StudentAccountAdminService service = new StudentAccountAdminService(
                jdbc, mock(AuditService.class), tokens, holds,
                mock(ResidencyService.class), roomChanges);

        Map<String, Object> result = service.resetState(
                100L, "202600000001", "重新初始化测试账号", operator);

        assertThat(result.get("accountStatus")).isEqualTo("PENDING");
    }

    private SqlSession residencySqlSession() throws Exception {
        Environment environment = new Environment(
                "runtime-test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        String resource = "mapper/residency/ResidencyMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration).openSession(true);
    }

    private void resetSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(), "root", "root-test-password");
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : List.of(
                    "room_assignment_history", "room_assignment", "room_bed_layout",
                    "active_batch_room_lock", "bed", "room", "dormitory_floor",
                    "dormitory_building", "allocation_run_result", "assignment_history",
                    "bed_assignment", "questionnaire_answer", "student_feature",
                    "team_invitation", "selection_team_member", "selection_team",
                    "student_notification", "active_batch_student_lock",
                    "batch_student_eligibility", "student", "app_user")) {
                statement.execute("DROP TABLE IF EXISTS " + table);
            }
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
            statement.execute("""
                    CREATE TABLE app_user (
                      id BIGINT PRIMARY KEY, student_id BIGINT NULL, user_type VARCHAR(32),
                      password_hash VARCHAR(255) NULL, account_status VARCHAR(32),
                      last_login_at DATETIME(3) NULL, welcome_acknowledged_at DATETIME(3) NULL,
                      version INT NOT NULL DEFAULT 0)
                    """);
            statement.execute("""
                    CREATE TABLE student (
                      id BIGINT PRIMARY KEY, student_number VARCHAR(32), student_name VARCHAR(128),
                      gender VARCHAR(8), student_category VARCHAR(32))
                    """);
            statement.execute("""
                    CREATE TABLE dormitory_building (
                      id BIGINT PRIMARY KEY, building_code VARCHAR(32), building_name VARCHAR(128),
                      enabled TINYINT, gender_restriction VARCHAR(8))
                    """);
            statement.execute("CREATE TABLE dormitory_floor (id BIGINT PRIMARY KEY, building_id BIGINT, floor_number INT)");
            statement.execute("""
                    CREATE TABLE room (
                      id BIGINT PRIMARY KEY, floor_id BIGINT, room_number VARCHAR(32), capacity INT,
                      resident_scope VARCHAR(32), gender_restriction VARCHAR(8),
                      operational_status VARCHAR(32))
                    """);
            statement.execute("""
                    CREATE TABLE bed (
                      id BIGINT PRIMARY KEY, room_id BIGINT, bed_code VARCHAR(32), bed_type VARCHAR(32),
                      position_index SMALLINT, operational_status VARCHAR(32))
                    """);
            statement.execute("""
                    CREATE TABLE room_bed_layout (
                      bed_id BIGINT PRIMARY KEY, layout_x DECIMAL(6,3), layout_z DECIMAL(6,3),
                      rotation_degrees SMALLINT)
                    """);
            statement.execute("CREATE TABLE active_batch_room_lock (room_id BIGINT PRIMARY KEY, batch_id BIGINT)");
            statement.execute("""
                    CREATE TABLE room_assignment (
                      id BIGINT PRIMARY KEY, batch_id BIGINT NULL, student_id BIGINT, room_id BIGINT,
                      bed_id BIGINT NULL, team_id BIGINT NULL, source_selection_mode VARCHAR(16),
                      assignment_method VARCHAR(32), assignment_status VARCHAR(16), assigned_by BIGINT NULL,
                      assigned_at DATETIME(3), bed_confirmed_at DATETIME(3) NULL,
                      ended_at DATETIME(3) NULL, end_reason VARCHAR(500) NULL,
                      version INT DEFAULT 0, created_at DATETIME(3), updated_at DATETIME(3))
                    """);
            statement.execute("""
                    CREATE TABLE room_assignment_history (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY, room_assignment_id BIGINT NULL,
                      student_id BIGINT, room_id BIGINT, bed_id BIGINT NULL, event_type VARCHAR(32),
                      operator_user_id BIGINT NULL, reason VARCHAR(500), previous_data JSON NULL,
                      current_data JSON NULL, occurred_at DATETIME(3))
                    """);
            statement.execute("CREATE TABLE allocation_run_result (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT)");
            statement.execute("CREATE TABLE assignment_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT)");
            statement.execute("CREATE TABLE bed_assignment (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT)");
            statement.execute("CREATE TABLE questionnaire_answer (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT)");
            statement.execute("CREATE TABLE student_feature (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT)");
            statement.execute("""
                    CREATE TABLE selection_team (
                      id BIGINT PRIMARY KEY, leader_student_id BIGINT, team_status VARCHAR(32), version INT DEFAULT 0)
                    """);
            statement.execute("""
                    CREATE TABLE selection_team_member (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY, team_id BIGINT, student_id BIGINT,
                      member_status VARCHAR(32), left_at DATETIME(3) NULL)
                    """);
            statement.execute("""
                    CREATE TABLE team_invitation (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY, team_id BIGINT, inviter_student_id BIGINT,
                      invitee_student_id BIGINT, invitation_status VARCHAR(32), responded_at DATETIME(3) NULL)
                    """);
            statement.execute("""
                    CREATE TABLE student_notification (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT, notification_type VARCHAR(32),
                      title_key VARCHAR(255), message_key VARCHAR(255), parameters_json JSON,
                      read_at DATETIME(3) NULL)
                    """);
            statement.execute("CREATE TABLE active_batch_student_lock (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT)");
            statement.execute("CREATE TABLE batch_student_eligibility (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT)");

            statement.execute("INSERT INTO student VALUES (100,'202600000001','测试学生','F','DOMESTIC')");
            statement.execute("INSERT INTO app_user VALUES (1,NULL,'ADMIN','x','ACTIVE',NULL,NULL,0)");
            statement.execute("INSERT INTO app_user VALUES (2,100,'STUDENT','x','ACTIVE',NULL,NULL,0)");
            statement.execute("INSERT INTO dormitory_building VALUES (1,'B01','一号楼',1,'F')");
            statement.execute("INSERT INTO dormitory_floor VALUES (10,1,3)");
            statement.execute("INSERT INTO room VALUES (12,10,'301',4,'MIXED','F','ENABLED')");
            statement.execute("INSERT INTO bed VALUES (21,12,'A01','LOFT_BED_DESK',1,'ENABLED')");
            statement.execute("INSERT INTO bed VALUES (22,12,'A02','LOFT_BED_DESK',2,'ENABLED')");
            statement.execute("INSERT INTO room_bed_layout VALUES (22,1.200,0.600,90)");
            statement.execute("""
                    INSERT INTO room_assignment VALUES
                    (900,NULL,100,12,21,NULL,'DIRECT','DIRECT_BED','ACTIVE',1,
                     CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),NULL,NULL,0,
                     CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
                    """);
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                + "/runtime_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static void awaitDatabase() throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(1).toNanos();
        SQLException latest = null;
        while (System.nanoTime() < deadline) {
            try (Connection ignored = DriverManager.getConnection(
                    jdbcUrl(), "root", "root-test-password")) {
                return;
            } catch (SQLException exception) {
                latest = exception;
                Thread.sleep(500L);
            }
        }
        throw new SQLException("MySQL Testcontainer 未在预期时间内就绪", latest);
    }
}
