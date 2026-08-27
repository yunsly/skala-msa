package com.lecture.enrollment.service;

import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentWriteService {

    private final EnrollmentRepository enrollmentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Enrollment createPendingEnrollment(Long userId, Long projectId, String reason) {
        return enrollmentRepository.save(
                Enrollment.builder()
                        .userId(userId)
                        .projectId(projectId)
                        .reason(reason)
                        .build()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Enrollment reapply(Enrollment enrollment, String reason) {
        enrollment.reapply(reason);
        return enrollmentRepository.save(enrollment);
    }
}
