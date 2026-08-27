package com.lecture.enrollment.service;

import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.kafka.EnrollmentKafkaProducer;
import com.lecture.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrollmentServiceTest {

    @Test
    void countsOnlyActiveProjectMembers() {
        EnrollmentRepository repository = mock(EnrollmentRepository.class);
        EnrollmentService service = new EnrollmentService(
                repository,
                mock(CourseServiceClient.class),
                mock(PaymentServiceClient.class),
                mock(EnrollmentKafkaProducer.class),
                mock(EnrollmentWriteService.class)
        );
        when(repository.countByProjectIdAndStatus(3L, Enrollment.Status.ACTIVE))
                .thenReturn(5L);

        long count = service.countActiveMembers(3L);

        assertThat(count).isEqualTo(5L);
        verify(repository).countByProjectIdAndStatus(3L, Enrollment.Status.ACTIVE);
    }
}
