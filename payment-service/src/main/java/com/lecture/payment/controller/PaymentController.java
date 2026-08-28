package com.lecture.payment.controller;

import com.lecture.payment.dto.PaymentDto;
import com.lecture.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/payments/internal/request - 내부 프로젝트 승인 티켓 생성
     */
    @PostMapping("/internal/request")
    public ResponseEntity<PaymentDto.InternalPaymentResult> processInternalPayment(
            @Valid @RequestBody PaymentDto.InternalPaymentRequest request) {

        PaymentDto.InternalPaymentResult result = paymentService.processInternalPayment(request);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/payments/pending - 내가 리더인 프로젝트로 들어온 승인 대기 목록
     */
    @GetMapping("/pending")
    public ResponseEntity<PaymentDto.ApiResponse<List<PaymentDto.PendingApprovalResponse>>> getPending(
            @RequestHeader("X-User-Id") Long approverId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return ResponseEntity.ok(PaymentDto.ApiResponse.success(
                paymentService.getPendingApprovals(approverId, authorization)));
    }

    /**
     * POST /api/payments/{id}/approve - 접근 신청 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<PaymentDto.ApiResponse<PaymentDto.PaymentResponse>> approve(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long approverId,
            @RequestBody(required = false) PaymentDto.DecisionRequest request
    ) {
        String reason = request == null ? null : request.getDecisionReason();
        return ResponseEntity.ok(PaymentDto.ApiResponse.success(
                paymentService.approve(id, approverId, reason)));
    }

    /**
     * POST /api/payments/{id}/reject - 접근 신청 거절
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<PaymentDto.ApiResponse<PaymentDto.PaymentResponse>> reject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long approverId,
            @RequestBody(required = false) PaymentDto.DecisionRequest request
    ) {
        String reason = request == null ? null : request.getDecisionReason();
        return ResponseEntity.ok(PaymentDto.ApiResponse.success(
                paymentService.reject(id, approverId, reason)));
    }

    /**
     * GET /api/payments/{id} - 프로젝트 승인 티켓 단건 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentDto.ApiResponse<PaymentDto.PaymentResponse>> getPayment(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                PaymentDto.ApiResponse.success(paymentService.getPayment(id)));
    }

    /**
     * GET /api/payments/user/{userId} - 사용자 프로젝트 승인 내역 조회
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<PaymentDto.ApiResponse<List<PaymentDto.PaymentResponse>>> getPaymentsByUser(
            @PathVariable Long userId) {

        return ResponseEntity.ok(
                PaymentDto.ApiResponse.success(paymentService.getPaymentsByUser(userId)));
    }
}
