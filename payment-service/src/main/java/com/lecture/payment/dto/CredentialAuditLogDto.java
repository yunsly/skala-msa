package com.lecture.payment.dto;

import com.lecture.payment.entity.CredentialAuditLog;
import lombok.*;

import java.time.LocalDateTime;

public class CredentialAuditLogDto {

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
}
