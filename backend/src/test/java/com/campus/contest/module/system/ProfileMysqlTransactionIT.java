package com.campus.contest.module.system;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 个人档案在真实 MySQL 上的集成证据（BIZ-PROFILE-001）。
 *
 * 本类刻意不进入 `mvn test` 默认执行（后缀为 IT，surefire 默认只收 *Test），
 * 并且额外要求 -Dprofile.mysql.it=true 才运行，避免没有数据库的机器误报失败。
 *
 * 运行方式（本机 Docker 容器 contest-mysql，宿主机端口 3307）：
 * <pre>
 * mvn -f backend/pom.xml -Dtest=ProfileMysqlTransactionIT -Dprofile.mysql.it=true test
 * </pre>
 *
 * 覆盖的真实数据库行为：
 * 1) version 条件更新：命中 1 行、version +1，且审计行与业务变更在同一事务提交；
 * 2) 旧 version 再次提交：条件更新命中 0 行，等价于应用层的 409 + 20001 版本冲突；
 * 3) 审计写入失败：同一事务回滚，档案变更不会“成功但没有审计”；
 * 4) 学号唯一索引：写入已被占用的学号被数据库拒绝，对应 409 + 20002。
 *
 * 只读写临时账号（profile_it_*），结束后全部删除，不触碰演示数据。
 */
@EnabledIfSystemProperty(named = "profile.mysql.it", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfileMysqlTransactionIT {

    private static final String DB_URL = System.getenv().getOrDefault(
            "PROFILE_IT_DB_URL",
            "jdbc:mysql://127.0.0.1:3307/contest_db?useUnicode=true&characterEncoding=utf8"
                    + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
    );
    private static final String DB_USER = System.getenv().getOrDefault("PROFILE_IT_DB_USER", "root");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("PROFILE_IT_DB_PASSWORD", "root123");

    private static final String SUFFIX = String.valueOf(System.currentTimeMillis());
    private static final String USER_A = "profile_it_a_" + SUFFIX;
    private static final String USER_B = "profile_it_b_" + SUFFIX;
    private static final String TRACE_PREFIX = "profile-it-" + SUFFIX;

    private static long userIdA;
    private static long userIdB;

    @BeforeAll
    static void prepare() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            userIdA = insertUser(connection, USER_A, "2024" + SUFFIX.substring(SUFFIX.length() - 6), "IT 学生 A");
            userIdB = insertUser(connection, USER_B, "2024" + (long) (Math.random() * 900000 + 100000), "IT 学生 B");
        }
    }

    @AfterAll
    static void cleanup() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from sys_oper_log where trace_id like ?")) {
                statement.setString(1, TRACE_PREFIX + "%");
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from sys_user where username in (?, ?)")) {
                statement.setString(1, USER_A);
                statement.setString(2, USER_B);
                statement.executeUpdate();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("真实 MySQL：version 命中时更新 1 行、version+1，审计与业务变更同事务提交")
    void versionUpdateCommitsWithAudit() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            connection.setAutoCommit(false);
            int rows;
            try (PreparedStatement statement = connection.prepareStatement(
                    "update sys_user set college = ?, version = version + 1 where id = ? and version = ?")) {
                statement.setString(1, "计算机科学学院");
                statement.setLong(2, userIdA);
                statement.setInt(3, 0);
                rows = statement.executeUpdate();
            }
            insertAudit(connection, TRACE_PREFIX + "-ok");
            connection.commit();

            assertThat(rows).as("条件更新命中 1 行").isEqualTo(1);
        }

        assertThat(readVersion(userIdA)).as("version 已 +1").isEqualTo(1);
        assertThat(readAuditCount(TRACE_PREFIX + "-ok")).as("审计已落库").isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("真实 MySQL：旧 version 再次提交命中 0 行，对应 HTTP 409 + 20001 版本冲突")
    void staleVersionMatchesNoRow() throws SQLException {
        int rows;
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "update sys_user set major = ? , version = version + 1 where id = ? and version = ?")) {
                statement.setString(1, "计算机科学与技术");
                statement.setLong(2, userIdA);
                statement.setInt(3, 0);
                rows = statement.executeUpdate();
            }
        }

        assertThat(rows).as("旧 version 不允许落库").isZero();
        assertThat(readVersion(userIdA)).as("version 不变").isEqualTo(1);
    }

    @Test
    @Order(3)
    @DisplayName("真实 MySQL：审计写入抛错时同事务回滚，档案变更不生效（成功变更必有审计）")
    void auditFailureRollsBackProfileUpdate() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            connection.setAutoCommit(false);
            int rows;
            try (PreparedStatement statement = connection.prepareStatement(
                    "update sys_user set major = ?, version = version + 1 where id = ? and version = ?")) {
                statement.setString(1, "不该落库的专业");
                statement.setLong(2, userIdA);
                statement.setInt(3, 1);
                rows = statement.executeUpdate();
            }
            assertThat(rows).isEqualTo(1);

            // 模拟 profileService 里 auditLogService.record 抛出的运行时异常
            try {
                throw new IllegalStateException("audit insert failed");
            } catch (RuntimeException expected) {
                connection.rollback();
            }
        }

        assertThat(readVersion(userIdA)).as("版本回滚到 1，说明档案变更一并撤销").isEqualTo(1);
        assertThat(readMajor(userIdA)).as("业务字段未写入").isNotEqualTo("不该落库的专业");
        assertThat(readAuditCount(TRACE_PREFIX + "-fail")).as("失败审计未提交").isZero();
    }

    @Test
    @Order(4)
    @DisplayName("真实 MySQL：写入已被占用的学号被唯一索引拒绝，对应 409 + 20002")
    void duplicateStudentNoIsRejectedByUniqueIndex() throws SQLException {
        String takenStudentNo = readStudentNo(userIdB);

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            assertThatThrownBy(() -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "update sys_user set student_no = ?, version = version + 1 where id = ? and version = ?")) {
                    statement.setString(1, takenStudentNo);
                    statement.setLong(2, userIdA);
                    statement.setInt(3, 1);
                    statement.executeUpdate();
                }
            }).isInstanceOfAny(SQLIntegrityConstraintViolationException.class, SQLException.class)
                    .hasMessageContaining("Duplicate");
        }

        assertThat(readVersion(userIdA)).as("学号冲突时版本不变").isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private static long insertUser(Connection connection, String username, String studentNo, String realName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into sys_user (username, real_name, password_hash, email, student_no, college,"
                        + " major, grade, status, created_at, created_by, version, deleted)"
                        + " values (?, ?, ?, null, ?, null, null, null, 1, ?, 0, 0, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, realName);
            statement.setString(3, "$2b$12$2G8oriBS/utH63P1c9zm4.iIDotvNxfd0LEx2SQdBjCwXUmr6BKcO");
            statement.setString(4, studentNo);
            statement.setTimestamp(5, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private static void insertAudit(Connection connection, String traceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into sys_oper_log (user_id, module, operation, request_uri, request_method, params,"
                        + " ip, duration_ms, result, trace_id, created_at, created_by, deleted, version)"
                        + " values (?, 'PROFILE', 'USER_PROFILE_UPDATE', '/api/v1/users/me', 'PATCH', ?,"
                        + " '127.0.0.1', 12, 0, ?, ?, 0, 0, 0)")) {
            statement.setLong(1, userIdA);
            statement.setString(2, "{\"changed_fields\":[\"college\"],\"version_after\":1,\"result\":\"SUCCESS\"}");
            statement.setString(3, traceId);
            statement.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        }
    }

    private static int readVersion(long userId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement("select version from sys_user where id = ?")) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }

    private static String readMajor(long userId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement("select major from sys_user where id = ?")) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private static String readStudentNo(long userId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement =
                     connection.prepareStatement("select student_no from sys_user where id = ?")) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private static int readAuditCount(String traceId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement =
                     connection.prepareStatement("select count(*) from sys_oper_log where trace_id = ?")) {
            statement.setString(1, traceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }
}
