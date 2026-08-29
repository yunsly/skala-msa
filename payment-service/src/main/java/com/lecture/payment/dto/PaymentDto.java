package com.lecture.payment.dto;

import com.lecture.payment.entity.Payment;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

public class PaymentDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InternalPaymentRequest {
        @NotNull(message = "접근 신청 ID는 필수입니다")
        private Long enrollmentId;

        @NotNull(message = "사용자 ID는 필수입니다")
        private Long userId;

        @NotNull(message = "프로젝트 ID는 필수입니다")
        private Long projectId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentResponse {
        private Long paymentId;
        private Long enrollmentId;
        private Long userId;
        private Long projectId;
        private Long approvedBy;
        private Payment.Status status;
        private String transactionId;
        private String decisionReason;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static PaymentResponse from(Payment payment) {
            return PaymentResponse.builder()
                    .paymentId(payment.getId())
                    .enrollmentId(payment.getEnrollmentId())
                    .userId(payment.getUserId())
                    .projectId(payment.getProjectId())
                    .approvedBy(payment.getApprovedBy())
                    .status(payment.getStatus())
                    .transactionId(payment.getTransactionId())
                    .decisionReason(payment.getDecisionReason())
                    .createdAt(payment.getCreatedAt())
                    .updatedAt(payment.getUpdatedAt())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DecisionRequest {
        private String decisionReason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PendingApprovalResponse {
        private Long id;
        private Long enrollmentId;
        private Long projectId;
        private String projectName;
        private Long userId;
        private String userName;
        private String reason;
        private LocalDateTime createdAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActiveGrantResponse {
        private Long id;
        private Long enrollmentId;
        private Long projectId;
        private String projectName;
        private Long userId;
        private String userName;
        private String transactionId;
        private LocalDateTime approvedAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InternalPaymentResult {
        private Long paymentId;
        private String status;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message("성공")
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
