package com.lecture.payment.service;

import com.lecture.payment.client.ApprovalContextClient;
import com.lecture.payment.dto.CredentialAuditLogDto;
import com.lecture.payment.dto.PaymentDto;
import com.lecture.payment.entity.CredentialAuditLog;
import com.lecture.payment.entity.Payment;
import com.lecture.payment.kafka.PaymentKafkaProducer;
import com.lecture.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ApprovalContextClient approvalContextClient;
    private final PaymentKafkaProducer kafkaProducer;
    private final CredentialAuditLogService auditLogService;

    /**
     * 프로젝트 접근 신청에 대응하는 승인 대기 티켓만 생성한다.
     * 승인/거절 및 Kafka 발행은 별도 기능 이슈에서 구현한다.
     */
    @Transactional
    public PaymentDto.InternalPaymentResult processInternalPayment(
            PaymentDto.InternalPaymentRequest request
    ) {
        Payment payment = paymentRepository.save(
                Payment.builder()
                        .enrollmentId(request.getEnrollmentId())
                        .userId(request.getUserId())
                        .projectId(request.getProjectId())
                        .build()
        );

        log.info(
                "[PaymentService] 프로젝트 승인 티켓 생성 - paymentId: {}, enrollmentId: {}",
                payment.getId(),
                payment.getEnrollmentId()
        );
        return PaymentDto.InternalPaymentResult.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus().name())
                .build();
    }

    public PaymentDto.PaymentResponse getPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "승인 티켓을 찾을 수 없습니다: " + id
                ));
        return PaymentDto.PaymentResponse.from(payment);
    }

    public List<PaymentDto.PaymentResponse> getPaymentsByUser(Long userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(PaymentDto.PaymentResponse::from)
                .toList();
    }

    /**
     * 승인자(X-User-Id)가 리더인 프로젝트로 들어온 PENDING 승인 티켓 목록.
     * 프로젝트명/신청자명/신청사유는 다른 서비스에서 best-effort 로 보강한다.
     */
    public List<PaymentDto.PendingApprovalResponse> getPendingApprovals(
            Long approverId, String authorizationHeader
    ) {
        List<ApprovalContextClient.OwnedProject> ownedProjects =
                approvalContextClient.getOwnedProjects(approverId, authorizationHeader);
        if (ownedProjects.isEmpty()) {
            return List.of();
        }

        Map<Long, String> projectNames = ownedProjects.stream()
                .collect(Collectors.toMap(
                        ApprovalContextClient.OwnedProject::id,
                        ApprovalContextClient.OwnedProject::name,
                        (a, b) -> a
                ));

        List<Payment> pending = paymentRepository.findByStatusAndProjectIdInOrderByCreatedAtAsc(
                Payment.Status.PENDING, List.copyOf(projectNames.keySet()));

        return pending.stream()
                .map(p -> PaymentDto.PendingApprovalResponse.builder()
                        .id(p.getId())
                        .enrollmentId(p.getEnrollmentId())
                        .projectId(p.getProjectId())
                        .projectName(projectNames.get(p.getProjectId()))
                        .userId(p.getUserId())
                        // 신청자 이름: 실행 중 user-service 가 ADMIN 만 타 사용자 조회를 허용하고
                        // payment-service 에 서비스 토큰 인프라가 없어 현재는 채우지 않는다(프론트가 fallback 처리).
                        .userName(null)
                        .reason(approvalContextClient.getEnrollmentReason(p.getEnrollmentId()))
                        .createdAt(p.getCreatedAt())
                        .build())
                .toList();
    }

    public List<PaymentDto.ActiveGrantResponse> getActiveGrants(
            Long approverId, String authorizationHeader
    ) {
        String role = approvalContextClient.getUserRole(approverId);
        boolean admin = "ADMIN".equals(role);
        List<ApprovalContextClient.OwnedProject> projects;
        if (admin) {
            projects = approvalContextClient.getAllProjects(authorizationHeader);
        } else if ("LEADER".equals(role)) {
            projects = approvalContextClient.getOwnedProjects(approverId, authorizationHeader);
        } else {
            throw new AccessDeniedException("프로젝트 리더 또는 ADMIN만 활성 멤버를 조회할 수 있습니다.");
        }
        if (projects.isEmpty()) {
            return List.of();
        }

        Map<Long, String> projectNames = projects.stream()
                .collect(Collectors.toMap(
                        ApprovalContextClient.OwnedProject::id,
                        ApprovalContextClient.OwnedProject::name,
                        (a, b) -> a
                ));
        List<Payment> active = admin
                ? paymentRepository.findByStatusOrderByUpdatedAtDesc(Payment.Status.COMPLETED)
                : paymentRepository.findByStatusAndProjectIdInOrderByUpdatedAtDesc(
                        Payment.Status.COMPLETED,
                        List.copyOf(projectNames.keySet())
                );

        return active.stream()
                .map(payment -> PaymentDto.ActiveGrantResponse.builder()
                        .id(payment.getId())
                        .enrollmentId(payment.getEnrollmentId())
                        .projectId(payment.getProjectId())
                        .projectName(projectNames.get(payment.getProjectId()))
                        .userId(payment.getUserId())
                        .userName(null)
                        .transactionId(payment.getTransactionId())
                        .approvedAt(payment.getUpdatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public PaymentDto.PaymentResponse approve(Long paymentId, Long approverId, String reason) {
        Payment payment = findPending(paymentId);
        payment.approve(approverId, UUID.randomUUID().toString(), reason);

        kafkaProducer.publishPaymentCompleted(
                PaymentKafkaProducer.PaymentCompletedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .paymentId(payment.getId())
                        .enrollmentId(payment.getEnrollmentId())
                        .userId(payment.getUserId())
                        .projectId(payment.getProjectId())
                        .approvedBy(approverId)
                        .transactionId(payment.getTransactionId())
                        .status(payment.getStatus().name())
                        .occurredAt(Instant.now().toString())
                        .build()
        );
        log.info("[PaymentService] 접근 승인 - paymentId: {}, approverId: {}", paymentId, approverId);
        return PaymentDto.PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentDto.PaymentResponse reject(Long paymentId, Long approverId, String reason) {
        Payment payment = findPending(paymentId);
        payment.reject(approverId, reason);

        kafkaProducer.publishPaymentRejected(
                PaymentKafkaProducer.PaymentRejectedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .paymentId(payment.getId())
                        .enrollmentId(payment.getEnrollmentId())
                        .userId(payment.getUserId())
                        .projectId(payment.getProjectId())
                        .rejectedBy(approverId)
                        .reason(reason)
                        .status(payment.getStatus().name())
                        .occurredAt(Instant.now().toString())
                        .build()
        );
        log.info("[PaymentService] 접근 거절 - paymentId: {}, approverId: {}", paymentId, approverId);
        return PaymentDto.PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentDto.PaymentResponse revoke(
            Long paymentId,
            Long approverId,
            String reason,
            String authorizationHeader
    ) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "승인 티켓을 찾을 수 없습니다: " + paymentId
                ));
        requireRevocationAuthority(payment, approverId, authorizationHeader);
        payment.revoke(approverId, requireDecisionReason(reason));

        kafkaProducer.publishPaymentRevoked(
                PaymentKafkaProducer.PaymentRevokedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .paymentId(payment.getId())
                        .enrollmentId(payment.getEnrollmentId())
                        .userId(payment.getUserId())
                        .projectId(payment.getProjectId())
                        .revokedBy(approverId)
                        .reason(reason)
                        .status(payment.getStatus().name())
                        .occurredAt(Instant.now().toString())
                        .build()
        );
        auditLogService.createAuditLog(
                CredentialAuditLogDto.CreateAuditLogRequest.builder()
                        .projectId(payment.getProjectId())
                        .userId(approverId)
                        .action(CredentialAuditLog.Action.PROJECT_ACCESS_REVOKED)
                        .result(CredentialAuditLog.Result.SUCCESS)
                        .detail("프로젝트 접근 권한 회수: " + reason)
                        .build()
        );
        log.info("[PaymentService] 접근 권한 회수 - paymentId: {}, approverId: {}",
                paymentId, approverId);
        return PaymentDto.PaymentResponse.from(payment);
    }

    private void requireRevocationAuthority(
            Payment payment,
            Long approverId,
            String authorizationHeader
    ) {
        String role = approvalContextClient.getUserRole(approverId);
        if ("ADMIN".equals(role)) {
            return;
        }
        boolean ownsProject = "LEADER".equals(role)
                && approvalContextClient.getOwnedProjects(approverId, authorizationHeader)
                .stream()
                .anyMatch(project -> project.id().equals(payment.getProjectId()));
        if (!ownsProject) {
            throw new AccessDeniedException("프로젝트 리더 또는 ADMIN만 접근 권한을 회수할 수 있습니다.");
        }
    }

    private String requireDecisionReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("회수 사유는 필수입니다.");
        }
        return reason.trim();
    }

    private Payment findPending(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "승인 티켓을 찾을 수 없습니다: " + paymentId
                ));
    }
}
