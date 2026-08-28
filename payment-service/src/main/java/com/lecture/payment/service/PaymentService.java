package com.lecture.payment.service;

import com.lecture.payment.client.ApprovalContextClient;
import com.lecture.payment.dto.PaymentDto;
import com.lecture.payment.entity.Payment;
import com.lecture.payment.kafka.PaymentKafkaProducer;
import com.lecture.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    private Payment findPending(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "승인 티켓을 찾을 수 없습니다: " + paymentId
                ));
    }
}
