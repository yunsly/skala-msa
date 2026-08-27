package com.lecture.course.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class CredentialAuditClient {

    private final WebClient webClient;
    private final ServiceTokenProvider tokenProvider;

    public CredentialAuditClient(
            WebClient.Builder webClientBuilder,
            ServiceTokenProvider tokenProvider,
            @Value("${service.payment-service.url}") String paymentServiceUrl
    ) {
        this.webClient = webClientBuilder.clone().baseUrl(paymentServiceUrl).build();
        this.tokenProvider = tokenProvider;
    }

    public void recordCredentialViewDenied(
            Long projectId,
            Long courseId,
            Long userId
    ) {
        AuditLogRequest request = new AuditLogRequest(
                projectId,
                courseId,
                userId,
                "CREDENTIAL_VIEWED",
                "DENIED",
                "프로젝트 자산 조회 권한이 없습니다."
        );

        try {
            webClient.post()
                    .uri("/api/payments/internal/audit-logs")
                    .headers(headers -> headers.setBearerAuth(
                            tokenProvider.getAccessToken()
                    ))
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (RuntimeException error) {
            log.warn(
                    "[CredentialAuditClient] 접근 거절 감사 이벤트 적재 실패 - projectId: {}, courseId: {}, userId: {}",
                    projectId,
                    courseId,
                    userId
            );
        }
    }

    private record AuditLogRequest(
            Long projectId,
            Long courseId,
            Long userId,
            String action,
            String result,
            String detail
    ) {
    }
}
