package com.lecture.enrollment.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.enrollment-completed}")
    private String enrollmentCompletedTopic;

    public void publishEnrollmentCompleted(KafkaEvent.EnrollmentCompletedEvent event) {
        kafkaTemplate.send(
                enrollmentCompletedTopic,
                String.valueOf(event.getProjectId()),
                event
        ).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Kafka Producer] enrollment.completed 발행 실패: {}", ex.getMessage());
            } else {
                log.info(
                        "[Kafka Producer] enrollment.completed 발행 성공 - enrollmentId: {}, offset: {}",
                        event.getEnrollmentId(),
                        result.getRecordMetadata().offset()
                );
            }
        });
    }
}
