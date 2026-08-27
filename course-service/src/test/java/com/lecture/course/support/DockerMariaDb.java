package com.lecture.course.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class DockerMariaDb implements AutoCloseable {

    private static final String PASSWORD = "credential-test-password";
    private static final String DATABASE = "lecture_db";

    private final String containerName = "credential-schema-it-" + UUID.randomUUID();
    private String jdbcUrl;
    private boolean started;

    public void start() {
        if (started) {
            return;
        }

        run(
                "docker", "run", "--detach", "--rm",
                "--name", containerName,
                "--env", "MARIADB_ROOT_PASSWORD=" + PASSWORD,
                "--env", "MARIADB_DATABASE=" + DATABASE,
                "--publish", "127.0.0.1::3306",
                "mariadb:11.2"
        );
        started = true;

        String port = run(
                "docker", "inspect",
                "--format={{(index (index .NetworkSettings.Ports \"3306/tcp\") 0).HostPort}}",
                containerName
        ).trim();
        jdbcUrl = "jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE;
        awaitReady();
    }

    public String getJdbcUrl() {
        start();
        return jdbcUrl;
    }

    public String getUsername() {
        return "root";
    }

    public String getPassword() {
        return PASSWORD;
    }

    private void awaitReady() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        Exception lastFailure = null;

        while (Instant.now().isBefore(deadline)) {
            try (Connection ignored = DriverManager.getConnection(
                    jdbcUrl,
                    getUsername(),
                    getPassword()
            )) {
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("MariaDB 준비 대기가 중단되었습니다.", interruptedException);
                }
            }
        }
        throw new IllegalStateException("MariaDB가 30초 안에 준비되지 않았습니다.", lastFailure);
    }

    private String run(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(
                        String.join(" ", command) + " 실행 실패: " + output.trim()
                );
            }
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Docker 명령을 실행할 수 없습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Docker 명령 실행이 중단되었습니다.", exception);
        }
    }

    @Override
    public void close() {
        if (!started) {
            return;
        }
        try {
            run("docker", "rm", "--force", containerName);
        } finally {
            started = false;
        }
    }
}
