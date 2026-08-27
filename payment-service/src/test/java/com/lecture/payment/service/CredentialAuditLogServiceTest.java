package com.lecture.payment.service;

import com.lecture.payment.dto.CredentialAuditLogDto;
import com.lecture.payment.entity.CredentialAuditLog;
import com.lecture.payment.repository.CredentialAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialAuditLogServiceTest {

    @Mock
    private CredentialAuditLogRepository auditLogRepository;

    @InjectMocks
    private CredentialAuditLogService auditLogService;

    @Test
    void createsServerGeneratedAuditEventWithoutLoggingSecretPayload() {
        CredentialAuditLogDto.CreateAuditLogRequest request =
                CredentialAuditLogDto.CreateAuditLogRequest.builder()
                        .projectId(1L)
                        .courseId(10L)
                        .userId(20L)
                        .action(CredentialAuditLog.Action.API_KEY_VIEWED)
                        .result(CredentialAuditLog.Result.DENIED)
                        .sourceIp("127.0.0.1")
                        .detail("프로젝트 접근 권한 없음")
                        .build();
        when(auditLogRepository.save(any(CredentialAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CredentialAuditLogDto.AuditLogResponse response =
                auditLogService.createAuditLog(request);

        assertThat(response.getProjectId()).isEqualTo(1L);
        assertThat(response.getCourseId()).isEqualTo(10L);
        assertThat(response.getResult()).isEqualTo(CredentialAuditLog.Result.DENIED);
        assertThatCodeIsUuid(response.getEventId());
    }

    @Test
    void returnsProjectLogsInRepositoryOrder() {
        CredentialAuditLog newest = auditLog("newest", 1L, 10L);
        CredentialAuditLog oldest = auditLog("oldest", 1L, 20L);
        when(auditLogRepository.findByProjectIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(newest, oldest));

        List<CredentialAuditLogDto.AuditLogResponse> responses =
                auditLogService.getProjectAuditLogs(1L);

        assertThat(responses).extracting(CredentialAuditLogDto.AuditLogResponse::getEventId)
                .containsExactly("newest", "oldest");
    }

    @Test
    void countsCredentialDeniedEventsWithinRequestedPeriod() {
        when(auditLogRepository.countByCourseIdAndResultAndCreatedAtGreaterThanEqual(
                any(), any(), any()
        )).thenReturn(3L);

        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(30)
                .minusSeconds(1);
        CredentialAuditLogDto.DeniedAccessCountResponse response =
                auditLogService.countDeniedAccesses(10L, 30);
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(30)
                .plusSeconds(1);

        ArgumentCaptor<LocalDateTime> periodStartCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepository).countByCourseIdAndResultAndCreatedAtGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(CredentialAuditLog.Result.DENIED),
                periodStartCaptor.capture()
        );
        assertThat(periodStartCaptor.getValue()).isBetween(before, after);
        assertThat(response.getCredentialId()).isEqualTo(10L);
        assertThat(response.getPeriodDays()).isEqualTo(30);
        assertThat(response.getDeniedAccessCount()).isEqualTo(3L);
    }

    private static CredentialAuditLog auditLog(
            String eventId,
            Long projectId,
            Long courseId
    ) {
        return CredentialAuditLog.builder()
                .eventId(eventId)
                .projectId(projectId)
                .courseId(courseId)
                .action(CredentialAuditLog.Action.API_KEY_VIEWED)
                .result(CredentialAuditLog.Result.SUCCESS)
                .build();
    }

    private static void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value)).isNotNull();
    }
}
