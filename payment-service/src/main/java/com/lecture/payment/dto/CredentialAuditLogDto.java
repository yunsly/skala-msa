package com.lecture.payment.dto;

import com.lecture.payment.entity.CredentialAuditLog;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

public class CredentialAuditLogDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateAuditLogRequest {
        private Long projectId;
        private Long courseId;
        private Long userId;

        @NotNull(message = "감사 이벤트 유형은 필수입니다")
        private CredentialAuditLog.Action action;

        @NotNull(message = "감사 이벤트 결과는 필수입니다")
        private CredentialAuditLog.Result result;

        @Size(max = 45, message = "요청 IP는 45자를 초과할 수 없습니다")
        private String sourceIp;

        @Size(max = 2000, message = "감사 상세 내용은 2000자를 초과할 수 없습니다")
        private String detail;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AuditLogResponse {
        private Long id;
        private String eventId;
        private Long projectId;
        private Long courseId;
        private Long userId;
        private CredentialAuditLog.Action action;
        private CredentialAuditLog.Result result;
        private String sourceIp;
        private String detail;
        private LocalDateTime createdAt;

        public static AuditLogResponse from(CredentialAuditLog log) {
            return AuditLogResponse.builder()
                    .id(log.getId())
                    .eventId(log.getEventId())
                    .projectId(log.getProjectId())
                    .courseId(log.getCourseId())
                    .userId(log.getUserId())
                    .action(log.getAction())
                    .result(log.getResult())
                    .sourceIp(log.getSourceIp())
                    .detail(log.getDetail())
                    .createdAt(log.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeniedAccessCountResponse {
        private Long credentialId;
        private int periodDays;
        private long deniedAccessCount;
        private LocalDateTime calculatedAt;
    }
}
