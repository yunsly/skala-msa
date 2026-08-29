package com.lecture.enrollment.kafka;

import com.lecture.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentKafkaConsumer {

    private final EnrollmentService enrollmentService;

    @KafkaListener(
            topics = "${kafka.topic.payment-completed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(Map<String, Object> event) {
        try {
            Long enrollmentId = requiredLong(event, "enrollmentId");
            Long userId = requiredLong(event, "userId");
            Long projectId = requiredLong(event, "projectId");
            enrollmentService.activateEnrollment(enrollmentId, userId, projectId);
        } catch (Exception e) {
            log.error(
                    "[Kafka Consumer] 프로젝트 접근 활성화 실패 - event: {}, error: {}",
                    event,
                    e.getMessage(),
                    e
            );
        }
    }

    @KafkaListener(
            topics = "${kafka.topic.payment-rejected}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentRejected(Map<String, Object> event) {
        try {
            Long enrollmentId = requiredLong(event, "enrollmentId");
            Long userId = requiredLong(event, "userId");
            Long projectId = requiredLong(event, "projectId");
            enrollmentService.cancelEnrollment(enrollmentId, userId, projectId);
        } catch (Exception e) {
            log.error(
                    "[Kafka Consumer] 프로젝트 접근 거절 처리 실패 - event: {}, error: {}",
                    event,
                    e.getMessage(),
                    e
            );
        }
    }

    @KafkaListener(
            topics = "${kafka.topic.payment-revoked}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentRevoked(Map<String, Object> event) {
        try {
            Long enrollmentId = requiredLong(event, "enrollmentId");
            Long userId = requiredLong(event, "userId");
            Long projectId = requiredLong(event, "projectId");
            enrollmentService.revokeEnrollment(enrollmentId, userId, projectId);
        } catch (RuntimeException error) {
            log.error(
                    "[Kafka Consumer] 프로젝트 접근 권한 회수 실패 - event: {}, error: {}",
                    event,
                    error.getMessage(),
                    error
            );
            throw error;
        }
    }

    private Long requiredLong(Map<String, Object> event, String field) {
        Object value = event.get(field);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Kafka 이벤트에 " + field + "가 없습니다.");
        }
        return number.longValue();
    }
}
