package com.lecture.course.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class EnrollmentServiceClient {

    private final WebClient webClient;
    private final ServiceTokenProvider tokenProvider;

    public EnrollmentServiceClient(
            WebClient.Builder webClientBuilder,
            ServiceTokenProvider tokenProvider,
            @Value("${service.enrollment-service.url}") String enrollmentServiceUrl
    ) {
        this.webClient = webClientBuilder.clone().baseUrl(enrollmentServiceUrl).build();
        this.tokenProvider = tokenProvider;
    }

    public List<Long> getActiveProjectIds(Long userId) {
        EnrollmentHistory response = webClient.get()
                .uri("/api/enrollments/internal/history/{userId}", userId)
                .headers(headers -> headers.setBearerAuth(tokenProvider.getAccessToken()))
                .retrieve()
                .bodyToMono(EnrollmentHistory.class)
                .block();
        return response == null || response.activeProjectIds() == null
                ? List.of()
                : response.activeProjectIds();
    }

    public long countActiveMembers(Long projectId) {
        Long response = webClient.get()
                .uri("/api/enrollments/internal/projects/{projectId}/active-count", projectId)
                .headers(headers -> headers.setBearerAuth(tokenProvider.getAccessToken()))
                .retrieve()
                .bodyToMono(Long.class)
                .block();
        return response == null ? 0L : response;
    }

    private record EnrollmentHistory(Long userId, List<Long> activeProjectIds) {
    }
}
