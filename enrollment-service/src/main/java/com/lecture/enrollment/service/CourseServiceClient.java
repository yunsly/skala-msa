package com.lecture.enrollment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseServiceClient {

    private final WebClient.Builder webClientBuilder;

    public boolean existsProject(Long projectId) {
        try {
            Boolean exists = webClientBuilder.build()
                    .get()
                    .uri(
                            "http://course-service/api/courses/internal/projects/{projectId}/exists",
                            projectId
                    )
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error(
                    "[CourseServiceClient] 프로젝트 존재 확인 실패 - projectId: {}, error: {}",
                    projectId,
                    e.getMessage()
            );
            throw new RuntimeException("Course Service 연결 실패", e);
        }
    }
}
