package com.lecture.enrollment.service;

import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.kafka.EnrollmentKafkaProducer;
import com.lecture.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrollmentServiceTest {

    private EnrollmentRepository repository;
    private CourseServiceClient courseServiceClient;
    private PaymentServiceClient paymentServiceClient;
    private EnrollmentWriteService writeService;
    private EnrollmentService service;

    @BeforeEach
    void setUp() {
        repository = mock(EnrollmentRepository.class);
        courseServiceClient = mock(CourseServiceClient.class);
        paymentServiceClient = mock(PaymentServiceClient.class);
        writeService = mock(EnrollmentWriteService.class);
        service = new EnrollmentService(
                repository,
                courseServiceClient,
                paymentServiceClient,
                mock(EnrollmentKafkaProducer.class),
                writeService
        );
    }

    private Enrollment enrollment(Long id, Long userId, Long projectId, Enrollment.Status status) {
        return Enrollment.builder()
                .id(id)
                .userId(userId)
                .projectId(projectId)
                .status(status)
                .reason("사유")
                .build();
    }

    // --- FR-03-01: 프로젝트 접근 권한 신청 ---

    @Test
    void enroll_createsPendingRequest_whenNoExistingEnrollment() {
        when(courseServiceClient.existsProject(10L)).thenReturn(true);
        when(repository.findByUserIdAndProjectId(1L, 10L)).thenReturn(Optional.empty());
        when(writeService.createPendingEnrollment(1L, 10L, "결제 모듈 개발"))
                .thenReturn(enrollment(100L, 1L, 10L, Enrollment.Status.PENDING));

        EnrollmentDto.EnrollmentResponse response = service.enroll(1L, 10L, "결제 모듈 개발");

        assertThat(response.getStatus()).isEqualTo(Enrollment.Status.PENDING);
        assertThat(response.getProjectId()).isEqualTo(10L);
        verify(paymentServiceClient).requestApproval(100L, 1L, 10L);
    }

    @Test
    void enroll_rejects_whenProjectDoesNotExist() {
        when(courseServiceClient.existsProject(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.enroll(1L, 999L, "사유"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 프로젝트");

        verify(repository, never()).findByUserIdAndProjectId(anyLong(), anyLong());
        verify(paymentServiceClient, never()).requestApproval(anyLong(), anyLong(), anyLong());
    }

    @Test
    void enroll_blocksDuplicate_whenPendingRequestExists() {
        when(courseServiceClient.existsProject(10L)).thenReturn(true);
        when(repository.findByUserIdAndProjectId(1L, 10L))
                .thenReturn(Optional.of(enrollment(100L, 1L, 10L, Enrollment.Status.PENDING)));

        assertThatThrownBy(() -> service.enroll(1L, 10L, "사유"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미");

        verify(paymentServiceClient, never()).requestApproval(anyLong(), anyLong(), anyLong());
    }

    @Test
    void enroll_blocksDuplicate_whenActiveMembershipExists() {
        when(courseServiceClient.existsProject(10L)).thenReturn(true);
        when(repository.findByUserIdAndProjectId(1L, 10L))
                .thenReturn(Optional.of(enrollment(100L, 1L, 10L, Enrollment.Status.ACTIVE)));

        assertThatThrownBy(() -> service.enroll(1L, 10L, "사유"))
                .isInstanceOf(IllegalStateException.class);

        verify(writeService, never()).reapply(any(), anyString());
        verify(paymentServiceClient, never()).requestApproval(anyLong(), anyLong(), anyLong());
    }

    @Test
    void enroll_allowsReapply_whenPreviousRequestCancelled() {
        Enrollment cancelled = enrollment(100L, 1L, 10L, Enrollment.Status.CANCELLED);
        when(courseServiceClient.existsProject(10L)).thenReturn(true);
        when(repository.findByUserIdAndProjectId(1L, 10L)).thenReturn(Optional.of(cancelled));
        when(writeService.reapply(cancelled, "다시 참여"))
                .thenReturn(enrollment(100L, 1L, 10L, Enrollment.Status.PENDING));

        EnrollmentDto.EnrollmentResponse response = service.enroll(1L, 10L, "다시 참여");

        assertThat(response.getStatus()).isEqualTo(Enrollment.Status.PENDING);
        verify(writeService).reapply(cancelled, "다시 참여");
        verify(paymentServiceClient).requestApproval(100L, 1L, 10L);
    }

    // --- FR-03-02: 내 프로젝트 목록 및 신청 현황 조회 ---

    @Test
    void getMyProjects_separatesActiveAndPending_andIgnoresCancelled() {
        when(repository.findByUserId(1L)).thenReturn(List.of(
                enrollment(1L, 1L, 10L, Enrollment.Status.ACTIVE),
                enrollment(2L, 1L, 20L, Enrollment.Status.PENDING),
                enrollment(3L, 1L, 30L, Enrollment.Status.CANCELLED)
        ));

        EnrollmentDto.MyProjectsResponse response = service.getMyProjects(1L);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getActiveProjects()).extracting(EnrollmentDto.EnrollmentResponse::getProjectId)
                .containsExactly(10L);
        assertThat(response.getPendingProjects()).extracting(EnrollmentDto.EnrollmentResponse::getProjectId)
                .containsExactly(20L);
    }

    @Test
    void getMyProjects_isScopedToRequestingUser() {
        service.getMyProjects(1L);

        // 조회는 요청자 ID로만 이루어져 타 사용자 데이터가 섞이지 않는다.
        verify(repository).findByUserId(1L);
        verify(repository, never()).findByUserId(eq(2L));
    }

    // --- Seat 계산 ---

    @Test
    void countsOnlyActiveProjectMembers() {
        when(repository.countByProjectIdAndStatus(3L, Enrollment.Status.ACTIVE))
                .thenReturn(5L);

        long count = service.countActiveMembers(3L);

        assertThat(count).isEqualTo(5L);
        verify(repository).countByProjectIdAndStatus(3L, Enrollment.Status.ACTIVE);
    }

    @Test
    void revokeEnrollment_cancelsActiveMembership() {
        Enrollment active = enrollment(100L, 1L, 10L, Enrollment.Status.ACTIVE);
        when(repository.findById(100L)).thenReturn(Optional.of(active));

        service.revokeEnrollment(100L, 1L, 10L);

        assertThat(active.getStatus()).isEqualTo(Enrollment.Status.CANCELLED);
    }

    @Test
    void revokeEnrollment_isIdempotentForCancelledMembership() {
        Enrollment cancelled = enrollment(100L, 1L, 10L, Enrollment.Status.CANCELLED);
        when(repository.findById(100L)).thenReturn(Optional.of(cancelled));

        service.revokeEnrollment(100L, 1L, 10L);

        assertThat(cancelled.getStatus()).isEqualTo(Enrollment.Status.CANCELLED);
    }

    @Test
    void revokeEnrollment_rejectsMismatchedEvent() {
        Enrollment active = enrollment(100L, 1L, 10L, Enrollment.Status.ACTIVE);
        when(repository.findById(100L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.revokeEnrollment(100L, 2L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일치하지 않습니다");

        assertThat(active.getStatus()).isEqualTo(Enrollment.Status.ACTIVE);
    }
}
