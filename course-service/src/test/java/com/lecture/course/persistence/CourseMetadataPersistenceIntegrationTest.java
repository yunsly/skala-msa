package com.lecture.course.persistence;

import com.lecture.course.entity.Course;
import com.lecture.course.entity.Project;
import com.lecture.course.repository.CourseRepository;
import com.lecture.course.repository.ProjectRepository;
import com.lecture.course.support.DockerMariaDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_INTEGRATION_TESTS", matches = "true")
class CourseMetadataPersistenceIntegrationTest {

    private static final String PLAINTEXT_SECRET = "demo-api-key-for-persistence-test";

    private static final DockerMariaDb MARIA_DB = new DockerMariaDb();

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeAll
    static void setEncryptionKey() {
        MARIA_DB.start();
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        System.setProperty(
                SecretMetadataConverter.KEY_PROPERTY,
                Base64.getEncoder().encodeToString(key)
        );
    }

    @AfterAll
    static void clearEncryptionKey() {
        try {
            System.clearProperty(SecretMetadataConverter.KEY_PROPERTY);
        } finally {
            MARIA_DB.close();
        }
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIA_DB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIA_DB::getUsername);
        registry.add("spring.datasource.password", MARIA_DB::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add(
                "spring.sql.init.schema-locations",
                () -> "file:../init-db/01_init.sql"
        );
    }

    @Test
    void storesEncryptedMetadataAndRestoresPlaintextThroughJpa() {
        jdbcTemplate.update(
                "INSERT INTO users (email, password, name, role) VALUES (?, ?, ?, ?)",
                "leader@example.com",
                "bcrypt-hash",
                "프로젝트 리더",
                "LEADER"
        );
        Long ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                "leader@example.com"
        );
        Project project = projectRepository.saveAndFlush(
                Project.builder()
                        .name("암호화 통합 테스트")
                        .ownerId(ownerId)
                        .build()
        );

        Course saved = courseRepository.saveAndFlush(
                Course.builder()
                        .projectId(project.getId())
                        .title("OpenAI Demo Key")
                        .category(Course.Category.API_KEY)
                        .provider("OpenAI")
                        .instructorId(ownerId)
                        .expiresAt(LocalDateTime.now().plusDays(30))
                        .metadata(PLAINTEXT_SECRET)
                        .build()
        );

        String rawMetadata = jdbcTemplate.queryForObject(
                "SELECT metadata FROM courses WHERE id = ?",
                String.class,
                saved.getId()
        );

        assertThat(rawMetadata)
                .startsWith("ENC:v1:")
                .doesNotContain(PLAINTEXT_SECRET);
        entityManager.clear();
        assertThat(courseRepository.findById(saved.getId()))
                .get()
                .extracting(Course::getMetadata)
                .isEqualTo(PLAINTEXT_SECRET);
    }
}
