package com.lecture.enrollment.kafka;

import com.lecture.enrollment.service.EnrollmentService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class EnrollmentKafkaConsumerTest {

    private final EnrollmentService enrollmentService = mock(EnrollmentService.class);
    private final EnrollmentKafkaConsumer consumer =
            new EnrollmentKafkaConsumer(enrollmentService);

    @Test
    void handlesPaymentRevokedEvent() {
        consumer.handlePaymentRevoked(Map.of(
                "enrollmentId", 100L,
                "userId", 7L,
                "projectId", 3L
        ));

        verify(enrollmentService).revokeEnrollment(100L, 7L, 3L);
    }

    @Test
    void propagatesFailureSoKafkaCanRetry() {
        RuntimeException failure = new IllegalStateException("temporary failure");
        doThrow(failure).when(enrollmentService).revokeEnrollment(100L, 7L, 3L);

        assertThatThrownBy(() -> consumer.handlePaymentRevoked(Map.of(
                "enrollmentId", 100L,
                "userId", 7L,
                "projectId", 3L
        ))).isSameAs(failure);
    }
}
