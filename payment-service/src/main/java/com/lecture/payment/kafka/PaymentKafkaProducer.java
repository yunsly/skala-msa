package com.lecture.payment.kafka;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.payment-completed}")
    private String paymentCompletedTopic;

    @Value("${kafka.topic.payment-rejected}")
    private String paymentRejectedTopic;

    @Value("${kafka.topic.payment-revoked}")
    private String paymentRevokedTopic;

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        publish(paymentCompletedTopic, event.getProjectId(), event, "승인", event.getPaymentId());
    }

    public void publishPaymentRejected(PaymentRejectedEvent event) {
        publish(paymentRejectedTopic, event.getProjectId(), event, "거절", event.getPaymentId());
    }

    public void publishPaymentRevoked(PaymentRevokedEvent event) {
        publish(paymentRevokedTopic, event.getProjectId(), event, "회수", event.getPaymentId());
    }

    private void publish(String topic, Long projectId, Object event, String label, Long paymentId) {
        try {
            SendResult<String, Object> result = kafkaTemplate
                    .send(topic, String.valueOf(projectId), event)
                    .get(10, TimeUnit.SECONDS);
            log.info(
                    "[Kafka Producer] 프로젝트 접근 {} 이벤트 발행 - paymentId: {}, offset: {}",
                    label,
                    paymentId,
                    result.getRecordMetadata().offset()
            );
        } catch (Exception e) {
            throw new IllegalStateException(topic + " Kafka 발행 실패", e);
        }
    }

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
    public static class PaymentRejectedEvent {
        private String eventId;
        private Long paymentId;
        private Long enrollmentId;
        private Long userId;
        private Long projectId;
        private Long rejectedBy;
        private String reason;
        private String status;
        private String occurredAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentRevokedEvent {
        private String eventId;
        private Long paymentId;
        private Long enrollmentId;
        private Long userId;
        private Long projectId;
        private Long revokedBy;
        private String reason;
        private String status;
        private String occurredAt;
    }
}
