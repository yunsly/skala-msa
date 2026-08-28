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

    public EnrollmentDto.EnrollmentResponse getEnrollment(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "접근 신청을 찾을 수 없습니다: " + enrollmentId
                ));
        return EnrollmentDto.EnrollmentResponse.from(enrollment);
    }

    /**
     * payment.rejected 이벤트 수신 시 접근 신청을 CANCELLED 로 되돌린다.
     * (재신청은 CANCELLED 상태에서만 가능하므로 거절 = 취소로 처리한다.)
     */
    @Transactional
    public void cancelEnrollment(Long enrollmentId, Long userId, Long projectId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "접근 신청을 찾을 수 없습니다: " + enrollmentId
                ));

        if (!enrollment.getUserId().equals(userId)
                || !enrollment.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("거절 이벤트와 접근 신청 정보가 일치하지 않습니다.");
        }
        if (enrollment.getStatus() == Enrollment.Status.CANCELLED) {
            return;
        }
        if (enrollment.getStatus() != Enrollment.Status.PENDING) {
            throw new IllegalStateException("PENDING 접근 신청만 거절할 수 있습니다.");
        }

        enrollment.cancel();
        log.info("[EnrollmentService] 접근 신청 거절 처리 - enrollmentId: {}", enrollmentId);
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

    /**
     * FR-03-02: 본인이 승인된(ACTIVE) 프로젝트 목록과 신청 대기(PENDING) 목록을 상태별로 분리해 반환한다.
     * 조회 기준은 항상 요청자(X-User-Id)이므로 다른 사용자의 신청 내역은 노출되지 않는다.
     */
    public EnrollmentDto.MyProjectsResponse getMyProjects(Long userId) {
        List<Enrollment> enrollments = enrollmentRepository.findByUserId(userId);

        List<EnrollmentDto.EnrollmentResponse> activeProjects = enrollments.stream()
                .filter(e -> e.getStatus() == Enrollment.Status.ACTIVE)
                .map(EnrollmentDto.EnrollmentResponse::from)
                .toList();

        List<EnrollmentDto.EnrollmentResponse> pendingProjects = enrollments.stream()
                .filter(e -> e.getStatus() == Enrollment.Status.PENDING)
                .map(EnrollmentDto.EnrollmentResponse::from)
                .toList();

        List<EnrollmentDto.EnrollmentResponse> cancelledProjects = enrollments.stream()
                .filter(e -> e.getStatus() == Enrollment.Status.CANCELLED)
                .map(EnrollmentDto.EnrollmentResponse::from)
                .toList();

        return EnrollmentDto.MyProjectsResponse.builder()
                .userId(userId)
                .activeProjects(activeProjects)
                .pendingProjects(pendingProjects)
                .cancelledProjects(cancelledProjects)
                .build();
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
