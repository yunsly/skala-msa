package com.lecture.course.schema;

import com.lecture.course.support.DockerMariaDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_INTEGRATION_TESTS", matches = "true")
class CredentialSchemaIntegrationTest {

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
    void createsCredentialDomainSchemaWithoutLegacyColumns() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MARIA_DB.getJdbcUrl(),
                MARIA_DB.getUsername(),
                MARIA_DB.getPassword()
        )) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new FileSystemResource(
                            Path.of("..", "init-db", "01_init.sql").normalize()
                    )
            );

            assertThat(tableNames(connection)).contains(
                    "courses",
                    "credential_audit_logs",
                    "enrollments",
                    "payments",
                    "projects",
                    "users"
            );
            assertThat(columnNames(connection, "courses"))
                    .contains(
                            "project_id",
                            "provider",
                            "plan_name",
                            "expires_at",
                            "renewal_at",
                            "last_rotated_at",
                            "metadata"
                    )
                    .doesNotContain("price", "enrollment_count");
            assertThat(columnNames(connection, "enrollments"))
                    .contains("project_id", "reason", "last_accessed_at")
                    .doesNotContain("course_id", "expires_at");
            assertThat(columnNames(connection, "payments"))
                    .contains(
                            "enrollment_id",
                            "project_id",
                            "approved_by",
                            "decision_reason"
                    )
                    .doesNotContain("course_id", "amount", "expires_at");

            assertThat(foreignKeys(connection, "projects"))
                    .containsEntry("owner_id", "users");
            assertThat(foreignKeys(connection, "courses"))
                    .containsEntry("project_id", "projects")
                    .containsEntry("instructor_id", "users");
            assertThat(foreignKeys(connection, "enrollments"))
                    .containsEntry("user_id", "users")
                    .containsEntry("project_id", "projects");
            assertThat(foreignKeys(connection, "payments"))
                    .containsEntry("enrollment_id", "enrollments")
                    .containsEntry("user_id", "users")
                    .containsEntry("project_id", "projects")
                    .containsEntry("approved_by", "users");
            assertThat(foreignKeys(connection, "credential_audit_logs"))
                    .containsEntry("project_id", "projects")
                    .containsEntry("course_id", "courses")
                    .containsEntry("user_id", "users");

            assertThat(uniqueIndexes(connection, "users")).contains("uq_users_email");
            assertThat(uniqueIndexes(connection, "projects")).contains("uq_projects_name");
            assertThat(uniqueIndexes(connection, "courses"))
                    .contains("uq_courses_project_title");
            assertThat(uniqueIndexes(connection, "enrollments"))
                    .contains("uq_enrollments_user_project");
            assertThat(uniqueIndexes(connection, "payments"))
                    .contains("uq_payments_transaction_id")
                    .doesNotContain("enrollment_id");
            assertThat(uniqueIndexes(connection, "credential_audit_logs"))
                    .contains("uq_audit_event_id");
        }
    }

    private Set<String> tableNames(Connection connection) throws Exception {
        Set<String> names = new TreeSet<>();
        try (ResultSet resultSet = connection.getMetaData().getTables(
                null,
                null,
                "%",
                new String[]{"TABLE"}
        )) {
            while (resultSet.next()) {
                names.add(resultSet.getString("TABLE_NAME").toLowerCase());
            }
        }
        return names;
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

    private Map<String, String> foreignKeys(Connection connection, String table) throws Exception {
        Map<String, String> keys = new HashMap<>();
        try (ResultSet resultSet = connection.getMetaData().getImportedKeys(
                null,
                null,
                table
        )) {
            while (resultSet.next()) {
                keys.put(
                        resultSet.getString("FKCOLUMN_NAME").toLowerCase(),
                        resultSet.getString("PKTABLE_NAME").toLowerCase()
                );
            }
        }
        return keys;
    }

    private Set<String> uniqueIndexes(Connection connection, String table) throws Exception {
        Set<String> indexes = new HashSet<>();
        try (ResultSet resultSet = connection.getMetaData().getIndexInfo(
                null,
                null,
                table,
                true,
                false
        )) {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                if (indexName != null && !"primary".equalsIgnoreCase(indexName)) {
                    indexes.add(indexName.toLowerCase());
                }
            }
        }
        return indexes;
    }
}
