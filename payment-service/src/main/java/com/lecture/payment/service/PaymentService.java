package com.lecture.payment.service;

import com.lecture.payment.dto.PaymentDto;
import com.lecture.payment.entity.Payment;
import com.lecture.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

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
}
