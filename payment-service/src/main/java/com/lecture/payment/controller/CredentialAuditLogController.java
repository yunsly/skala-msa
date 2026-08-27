package com.lecture.payment.controller;

import com.lecture.payment.dto.CredentialAuditLogDto;
import com.lecture.payment.dto.PaymentDto;
import com.lecture.payment.service.CredentialAuditLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class CredentialAuditLogController {

    private final CredentialAuditLogService auditLogService;

    @PostMapping("/internal/audit-logs")
    public ResponseEntity<PaymentDto.ApiResponse<CredentialAuditLogDto.AuditLogResponse>> createAuditLog(
            @Valid @RequestBody CredentialAuditLogDto.CreateAuditLogRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PaymentDto.ApiResponse.success(
                        auditLogService.createAuditLog(request)
                ));
    }

    @GetMapping("/audit-logs/projects/{projectId}")
    public ResponseEntity<PaymentDto.ApiResponse<List<CredentialAuditLogDto.AuditLogResponse>>> getProjectAuditLogs(
            @PathVariable @Positive Long projectId
    ) {
        return ResponseEntity.ok(PaymentDto.ApiResponse.success(
                auditLogService.getProjectAuditLogs(projectId)
        ));
    }

    @GetMapping("/internal/audit-logs/credentials/{credentialId}/denied-count")
    public ResponseEntity<PaymentDto.ApiResponse<CredentialAuditLogDto.DeniedAccessCountResponse>> countDeniedAccesses(
            @PathVariable @Positive Long credentialId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days
    ) {
        return ResponseEntity.ok(PaymentDto.ApiResponse.success(
                auditLogService.countDeniedAccesses(credentialId, days)
        ));
    }
}
