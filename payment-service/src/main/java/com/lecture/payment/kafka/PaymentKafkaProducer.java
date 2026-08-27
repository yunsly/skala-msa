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

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        try {
            SendResult<String, Object> result = kafkaTemplate
                    .send(
                            paymentCompletedTopic,
                            String.valueOf(event.getProjectId()),
                            event
                    )
                    .get(10, TimeUnit.SECONDS);

            log.info(
                    "[Kafka Producer] 프로젝트 접근 승인 이벤트 발행 - paymentId: {}, offset: {}",
                    event.getPaymentId(),
                    result.getRecordMetadata().offset()
            );
        } catch (Exception e) {
            throw new IllegalStateException("payment.completed Kafka 발행 실패", e);
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
}
