package com.lecture.enrollment.kafka;

import lombok.*;

public class KafkaEvent {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentCompletedEvent {
        private String eventId;
        private Long paymentId;
        private Long enrollmentId;
        private Long userId;
        private Long projectId;
        private Long approvedBy;
        private String transactionId;
        private String status;
        private String occurredAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrollmentCompletedEvent {
        private Long enrollmentId;
        private Long userId;
        private Long projectId;
        private String status;
    }
}
