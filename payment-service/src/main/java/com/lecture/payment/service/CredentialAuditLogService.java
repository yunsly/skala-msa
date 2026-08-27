package com.lecture.payment.service;

import com.lecture.payment.dto.CredentialAuditLogDto;
import com.lecture.payment.entity.CredentialAuditLog;
import com.lecture.payment.repository.CredentialAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CredentialAuditLogService {

    private final CredentialAuditLogRepository auditLogRepository;

    @Transactional
    public CredentialAuditLogDto.AuditLogResponse createAuditLog(
            CredentialAuditLogDto.CreateAuditLogRequest request
    ) {
        CredentialAuditLog auditLog = CredentialAuditLog.builder()
                .eventId(UUID.randomUUID().toString())
                .projectId(request.getProjectId())
                .courseId(request.getCourseId())
                .userId(request.getUserId())
                .action(request.getAction())
                .result(request.getResult())
                .sourceIp(request.getSourceIp())
                .detail(request.getDetail())
                .build();

        CredentialAuditLog savedAuditLog = auditLogRepository.save(auditLog);
        log.info(
                "[CredentialAuditLog] 이벤트 저장 - eventId: {}, projectId: {}, courseId: {}, action: {}, result: {}",
                savedAuditLog.getEventId(),
                savedAuditLog.getProjectId(),
                savedAuditLog.getCourseId(),
                savedAuditLog.getAction(),
                savedAuditLog.getResult()
        );
        return CredentialAuditLogDto.AuditLogResponse.from(savedAuditLog);
    }

    public List<CredentialAuditLogDto.AuditLogResponse> getProjectAuditLogs(
            Long projectId
    ) {
        return auditLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(CredentialAuditLogDto.AuditLogResponse::from)
                .toList();
    }

    public CredentialAuditLogDto.DeniedAccessCountResponse countDeniedAccesses(
            Long credentialId,
            int periodDays
    ) {
        LocalDateTime calculatedAt = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime periodStart = calculatedAt.minusDays(periodDays);
        long deniedAccessCount = auditLogRepository
                .countByCourseIdAndResultAndCreatedAtGreaterThanEqual(
                        credentialId,
                        CredentialAuditLog.Result.DENIED,
                        periodStart
                );

        return CredentialAuditLogDto.DeniedAccessCountResponse.builder()
                .credentialId(credentialId)
                .periodDays(periodDays)
                .deniedAccessCount(deniedAccessCount)
                .calculatedAt(calculatedAt)
                .build();
    }
}
