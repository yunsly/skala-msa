package com.lecture.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 승인 대기 화면에 필요한 부가 정보(내가 리더인 프로젝트 / 신청자 이름 / 신청 사유)를
 * 다른 서비스에서 조회한다. 모든 호출은 best-effort 로, 실패해도 승인 목록 자체는 반환된다.
 */
@Slf4j
@Component
public class ApprovalContextClient {

    private final RestClient courseClient;
    private final RestClient enrollmentClient;
    private final RestClient userClient;

    public ApprovalContextClient(
            @Value("${service.course-service.url}") String courseUrl,
            @Value("${service.enrollment-service.url}") String enrollmentUrl,
            @Value("${service.user-service.url}") String userUrl
    ) {
        this.courseClient = RestClient.create(courseUrl);
        this.enrollmentClient = RestClient.create(enrollmentUrl);
        this.userClient = RestClient.create(userUrl);
    }

    public record OwnedProject(Long id, String name) {
    }

    /**
     * 승인자(approverId)가 소유(리더)한 프로젝트 목록. course-service 의 사용자 인증이
     * 필요한 엔드포인트라 게이트웨이가 넘겨준 Authorization 헤더를 그대로 포워딩한다.
     */
    @SuppressWarnings("unchecked")
    public List<OwnedProject> getOwnedProjects(Long approverId, String authorizationHeader) {
        return getProjects(authorizationHeader).stream()
                .filter(project -> project.ownerId() != null
                        && project.ownerId().equals(approverId))
                .map(project -> new OwnedProject(project.id(), project.name()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<OwnedProject> getAllProjects(String authorizationHeader) {
        return getProjects(authorizationHeader).stream()
                .map(project -> new OwnedProject(project.id(), project.name()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<ProjectContext> getProjects(String authorizationHeader) {
        try {
            Map<String, Object> body = courseClient.get()
                    .uri("/api/courses/projects")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> data = body == null
                    ? List.of()
                    : (List<Map<String, Object>>) body.getOrDefault("data", List.of());

            return data.stream()
                    .map(p -> new ProjectContext(
                            asLong(p.get("id")),
                            (String) p.get("name"),
                            asLong(p.get("ownerId"))
                    ))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("[ApprovalContextClient] 프로젝트 조회 실패 - error: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public String getUserRole(Long userId) {
        try {
            Map<String, Object> body = userClient.get()
                    .uri("/api/users/internal/{id}", userId)
                    .retrieve()
                    .body(Map.class);
            Object role = body == null ? null : body.get("role");
            if (role == null) {
                throw new IllegalStateException("사용자 역할 응답이 없습니다.");
            }
            return String.valueOf(role);
        } catch (RuntimeException e) {
            log.warn("[ApprovalContextClient] 사용자 역할 조회 실패 - userId: {}, error: {}",
                    userId, e.getMessage());
            throw new IllegalStateException("사용자 역할을 확인할 수 없습니다.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public String getEnrollmentReason(Long enrollmentId) {
        try {
            Map<String, Object> body = enrollmentClient.get()
                    .uri("/api/enrollments/internal/{id}", enrollmentId)
                    .retrieve()
                    .body(Map.class);
            return body == null ? null : (String) body.get("reason");
        } catch (RuntimeException e) {
            log.warn("[ApprovalContextClient] 신청 사유 조회 실패 - enrollmentId: {}", enrollmentId);
            return null;
        }
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private record ProjectContext(Long id, String name, Long ownerId) {
    }
}
