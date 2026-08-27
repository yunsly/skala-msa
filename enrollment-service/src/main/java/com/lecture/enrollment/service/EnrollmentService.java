package com.lecture.enrollment.service;

import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.kafka.EnrollmentKafkaProducer;
import com.lecture.enrollment.kafka.KafkaEvent;
import com.lecture.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseServiceClient courseServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final EnrollmentKafkaProducer kafkaProducer;
    private final EnrollmentWriteService enrollmentWriteService;

    public EnrollmentDto.EnrollmentResponse enroll(
            Long userId,
            Long projectId,
            String reason
    ) {
        if (!courseServiceClient.existsProject(projectId)) {
            throw new IllegalArgumentException("존재하지 않는 프로젝트입니다: " + projectId);
        }

        Enrollment enrollment = enrollmentRepository
                .findByUserIdAndProjectId(userId, projectId)
                .map(existing -> {
                    if (existing.getStatus() != Enrollment.Status.CANCELLED) {
                        throw new IllegalStateException("이미 접근 신청했거나 활성화된 프로젝트입니다.");
                    }
                    return enrollmentWriteService.reapply(existing, reason);
                })
                .orElseGet(() -> enrollmentWriteService.createPendingEnrollment(
                        userId,
                        projectId,
                        reason
                ));

        paymentServiceClient.requestApproval(enrollment.getId(), userId, projectId);
        return EnrollmentDto.EnrollmentResponse.from(enrollment);
    }

    @Transactional
    public void activateEnrollment(Long enrollmentId, Long userId, Long projectId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "접근 신청을 찾을 수 없습니다: " + enrollmentId
                ));

        if (!enrollment.getUserId().equals(userId)
                || !enrollment.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("승인 이벤트와 접근 신청 정보가 일치하지 않습니다.");
        }
        if (enrollment.getStatus() == Enrollment.Status.ACTIVE) {
            return;
        }
        if (enrollment.getStatus() != Enrollment.Status.PENDING) {
            throw new IllegalStateException("PENDING 접근 신청만 활성화할 수 있습니다.");
        }

        enrollment.activate();
        kafkaProducer.publishEnrollmentCompleted(
                KafkaEvent.EnrollmentCompletedEvent.builder()
                        .enrollmentId(enrollment.getId())
                        .userId(userId)
                        .projectId(projectId)
                        .status("ACTIVE")
                        .build()
        );
    }

    public List<EnrollmentDto.EnrollmentResponse> getEnrollmentsByUser(Long userId) {
        return enrollmentRepository.findByUserId(userId).stream()
                .map(EnrollmentDto.EnrollmentResponse::from)
                .toList();
    }

    public EnrollmentDto.EnrollmentHistoryResponse getEnrollmentHistory(Long userId) {
        List<Long> activeProjectIds = enrollmentRepository
                .findByUserIdAndStatus(userId, Enrollment.Status.ACTIVE)
                .stream()
                .map(Enrollment::getProjectId)
                .toList();

        return EnrollmentDto.EnrollmentHistoryResponse.builder()
                .userId(userId)
                .activeProjectIds(activeProjectIds)
                .build();
    }

    public long countActiveMembers(Long projectId) {
        return enrollmentRepository.countByProjectIdAndStatus(
                projectId,
                Enrollment.Status.ACTIVE
        );
    }
}
