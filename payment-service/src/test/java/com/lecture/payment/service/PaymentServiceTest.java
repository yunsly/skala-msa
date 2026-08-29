package com.lecture.payment.service;

import com.lecture.payment.client.ApprovalContextClient;
import com.lecture.payment.dto.CredentialAuditLogDto;
import com.lecture.payment.dto.PaymentDto;
import com.lecture.payment.entity.Payment;
import com.lecture.payment.kafka.PaymentKafkaProducer;
import com.lecture.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private ApprovalContextClient approvalContextClient;
    private PaymentKafkaProducer kafkaProducer;
    private CredentialAuditLogService auditLogService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        approvalContextClient = mock(ApprovalContextClient.class);
        kafkaProducer = mock(PaymentKafkaProducer.class);
        auditLogService = mock(CredentialAuditLogService.class);
        paymentService = new PaymentService(
                paymentRepository,
                approvalContextClient,
                kafkaProducer,
                auditLogService
        );
    }

    @Test
    void projectLeaderCanRevokeCompletedGrant() {
        Payment payment = completedPayment(10L, 100L, 7L, 3L);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(approvalContextClient.getUserRole(2L)).thenReturn("LEADER");
        when(approvalContextClient.getOwnedProjects(2L, "Bearer token"))
                .thenReturn(List.of(new ApprovalContextClient.OwnedProject(3L, "Payment")));

        PaymentDto.PaymentResponse response = paymentService.revoke(
                10L,
                2L,
                "프로젝트 종료",
                "Bearer token"
        );

        assertThat(response.getStatus()).isEqualTo(Payment.Status.CANCELLED);
        assertThat(response.getApprovedBy()).isEqualTo(2L);

        ArgumentCaptor<PaymentKafkaProducer.PaymentRevokedEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentKafkaProducer.PaymentRevokedEvent.class);
        verify(kafkaProducer).publishPaymentRevoked(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEnrollmentId()).isEqualTo(100L);
        assertThat(eventCaptor.getValue().getProjectId()).isEqualTo(3L);
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("CANCELLED");
        verify(auditLogService).createAuditLog(any(CredentialAuditLogDto.CreateAuditLogRequest.class));
    }

    @Test
    void leaderCannotRevokeGrantFromAnotherProject() {
        Payment payment = completedPayment(10L, 100L, 7L, 3L);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(approvalContextClient.getUserRole(2L)).thenReturn("LEADER");
        when(approvalContextClient.getOwnedProjects(2L, "Bearer token"))
                .thenReturn(List.of(new ApprovalContextClient.OwnedProject(4L, "Other")));

        assertThatThrownBy(() -> paymentService.revoke(
                10L,
                2L,
                "권한 회수",
                "Bearer token"
        )).isInstanceOf(AccessDeniedException.class);

        verify(kafkaProducer, never()).publishPaymentRevoked(any());
        verify(auditLogService, never()).createAuditLog(any());
    }

    @Test
    void adminCanListAllActiveGrants() {
        Payment payment = completedPayment(10L, 100L, 7L, 3L);
        when(approvalContextClient.getUserRole(1L)).thenReturn("ADMIN");
        when(approvalContextClient.getAllProjects("Bearer token"))
                .thenReturn(List.of(new ApprovalContextClient.OwnedProject(3L, "Payment")));
        when(paymentRepository.findByStatusOrderByUpdatedAtDesc(Payment.Status.COMPLETED))
                .thenReturn(List.of(payment));

        List<PaymentDto.ActiveGrantResponse> response = paymentService.getActiveGrants(
                1L,
                "Bearer token"
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getProjectName()).isEqualTo("Payment");
        assertThat(response.getFirst().getUserId()).isEqualTo(7L);
    }

    @Test
    void revokeRequiresReason() {
        Payment payment = completedPayment(10L, 100L, 7L, 3L);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(approvalContextClient.getUserRole(1L)).thenReturn("ADMIN");

        assertThatThrownBy(() -> paymentService.revoke(
                10L,
                1L,
                " ",
                "Bearer token"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("회수 사유");

        verify(kafkaProducer, never()).publishPaymentRevoked(any());
    }

    private Payment completedPayment(
            Long id,
            Long enrollmentId,
            Long userId,
            Long projectId
    ) {
        return Payment.builder()
                .id(id)
                .enrollmentId(enrollmentId)
                .userId(userId)
                .projectId(projectId)
                .approvedBy(5L)
                .status(Payment.Status.COMPLETED)
                .transactionId("grant-ticket")
                .updatedAt(LocalDateTime.of(2026, 8, 29, 12, 0))
                .build();
    }
}
