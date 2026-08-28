package com.lecture.enrollment.service;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentServiceClient {

    private final WebClient.Builder webClientBuilder;
    private final ServiceTokenProvider tokenProvider;

    public PaymentResult requestApproval(
            Long enrollmentId,
            Long userId,
            Long projectId
    ) {
        try {
            PaymentRequest request = new PaymentRequest(enrollmentId, userId, projectId);
            return webClientBuilder.build()
                    .post()
                    .uri("http://payment-service:8084/api/payments/internal/request")
                    .headers(headers -> headers.setBearerAuth(
                            tokenProvider.getAccessToken()
                    ))
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(PaymentResult.class)
                    .block();
        } catch (Exception e) {
            log.error(
                    "[PaymentServiceClient] 승인 티켓 생성 실패 - enrollmentId: {}, error: {}",
                    enrollmentId,
                    e.getMessage()
            );
            throw new RuntimeException("Payment Service 연결 실패", e);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class PaymentRequest {
        private Long enrollmentId;
        private Long userId;
        private Long projectId;
    }

    @Getter
    @NoArgsConstructor
    public static class PaymentResult {
        private Long paymentId;
        private String status;
    }
}
