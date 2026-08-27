package com.lecture.course.schema;

import com.lecture.course.support.DockerMariaDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_INTEGRATION_TESTS", matches = "true")
class CredentialDomainMigrationIntegrationTest {

    private static final DockerMariaDb MARIA_DB = new DockerMariaDb();

    @BeforeAll
    static void startDatabase() {
        MARIA_DB.start();
    }

    @AfterAll
    static void stopDatabase() {
        MARIA_DB.close();
    }

    @Test
    void migratesLegacyLectureDataToProjectCredentialDomain() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MARIA_DB.getJdbcUrl(),
                MARIA_DB.getUsername(),
                MARIA_DB.getPassword()
        )) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("schema/legacy_lecture_schema.sql")
            );
            insertLegacyData(connection);

            ScriptUtils.executeSqlScript(
                    connection,
                    new FileSystemResource(
                            Path.of(
                                    "..",
                                    "init-db",
                                    "migrations",
                                    "V2__project_credential_domain.sql"
                            ).normalize()
                    )
            );

            assertThat(singleString(connection, "SELECT role FROM users WHERE id = 1"))
                    .isEqualTo("LEADER");
            assertThat(singleString(connection, "SELECT role FROM users WHERE id = 2"))
                    .isEqualTo("MEMBER");
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM projects")).isEqualTo(1L);
            assertThat(singleLong(connection, "SELECT project_id FROM courses WHERE id = 1"))
                    .isEqualTo(1L);
            assertThat(singleString(connection, "SELECT category FROM courses WHERE id = 1"))
                    .isEqualTo("SUBSCRIPTION_PLAN");
            assertThat(singleString(connection, "SELECT provider FROM courses WHERE id = 1"))
                    .isEqualTo("LEGACY_MIGRATION");
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM enrollments"))
                    .isEqualTo(2L);
            assertThat(singleString(
                    connection,
                    "SELECT status FROM enrollments WHERE user_id = 3 AND project_id = 1"
            )).isEqualTo("ACTIVE");
            assertThat(singleLong(connection, "SELECT project_id FROM payments WHERE id = 1"))
                    .isEqualTo(1L);
            assertThat(singleLong(connection, "SELECT enrollment_id FROM payments WHERE id = 1"))
                    .isNotNull();

            assertThat(columnNames(connection, "courses"))
                    .contains("project_id", "provider", "plan_name", "metadata")
                    .doesNotContain("price", "enrollment_count");
            assertThat(columnNames(connection, "enrollments"))
                    .contains("project_id", "reason", "last_accessed_at")
                    .doesNotContain("course_id");
            assertThat(columnNames(connection, "payments"))
                    .contains("enrollment_id", "project_id", "approved_by")
                    .doesNotContain("course_id", "amount");
            assertThat(tableExists(connection, "credential_audit_logs")).isTrue();
        }
    }

    private void insertLegacyData(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, email, password, name, role)
                    VALUES
                        (1, 'leader@example.com', 'hash', '리더', 'INSTRUCTOR'),
                        (2, 'pending@example.com', 'hash', '대기 멤버', 'STUDENT'),
                        (3, 'approved@example.com', 'hash', '승인 멤버', 'STUDENT')
                    """);
            statement.executeUpdate("""
                    INSERT INTO courses (
                        id, title, description, category, price,
                        instructor_id, enrollment_count, status
                    ) VALUES (
                        1, 'Legacy Backend Course', '기존 데이터', 'BACKEND', 10000,
                        1, 1, 'ACTIVE'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO enrollments (id, user_id, course_id, status)
                    VALUES (1, 2, 1, 'PENDING')
                    """);
            statement.executeUpdate("""
                    INSERT INTO payments (
                        id, user_id, course_id, amount, status, transaction_id
                    ) VALUES (1, 3, 1, 10000, 'COMPLETED', 'legacy-ticket')
                    """);
        }
    }

    private String singleString(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private Long singleLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(
                null,
                null,
                table,
                new String[]{"TABLE"}
        )) {
            return resultSet.next();
        }
    }

    private Set<String> columnNames(Connection connection, String table) throws Exception {
        Set<String> names = new TreeSet<>();
        try (ResultSet resultSet = connection.getMetaData().getColumns(
                null,
                null,
                table,
                "%"
        )) {
            while (resultSet.next()) {
                names.add(resultSet.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return names;
    }
}
