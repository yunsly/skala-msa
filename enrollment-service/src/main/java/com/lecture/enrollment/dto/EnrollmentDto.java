package com.lecture.enrollment.dto;

import com.lecture.enrollment.entity.Enrollment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class EnrollmentDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "[프로젝트 접근 권한 신청] MEMBER가 특정 프로젝트에 대한 접근 권한을 신청하는 요청")
    public static class EnrollRequest {
        @NotNull(message = "프로젝트 ID는 필수입니다")
        @Schema(description = "[사내 프로젝트] 접근을 신청할 대상 프로젝트(projects.id)", example = "1")
        private Long projectId;

        @Schema(description = "[신청 사유] 프로젝트 접근이 필요한 목적", example = "결제 MSA 프로젝트 개발 참여")
        private String reason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "[프로젝트 접근 권한] 신청 사원의 프로젝트 멤버십/신청 단건 정보")
    public static class EnrollmentResponse {
        @Schema(description = "[접근 신청 ID] enrollments.id")
        private Long id;

        @Schema(description = "[신청 사원] 신청한 사용자(users.id)")
        private Long userId;

        @Schema(description = "[사내 프로젝트] 대상 프로젝트(projects.id)")
        private Long projectId;

        @Schema(description = "[신청 사유] 프로젝트 접근이 필요한 목적")
        private String reason;

        @Schema(description = "[멤버십 상태] PENDING(승인 대기) | ACTIVE(승인 완료) | CANCELLED(회수/탈퇴)")
        private Enrollment.Status status;

        @Schema(description = "[최근 자산 접근 일시] 프로젝트 내 자산 Secret 평문 조회 시각")
        private LocalDateTime lastAccessedAt;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static EnrollmentResponse from(Enrollment enrollment) {
            return EnrollmentResponse.builder()
                    .id(enrollment.getId())
                    .userId(enrollment.getUserId())
                    .projectId(enrollment.getProjectId())
                    .reason(enrollment.getReason())
                    .status(enrollment.getStatus())
                    .lastAccessedAt(enrollment.getLastAccessedAt())
                    .createdAt(enrollment.getCreatedAt())
                    .updatedAt(enrollment.getUpdatedAt())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "[내 프로젝트] 본인이 승인된(ACTIVE) 프로젝트 목록과 신청 대기(PENDING) 목록을 상태별로 분리한 응답")
    public static class MyProjectsResponse {
        @Schema(description = "[신청 사원] 조회 기준 사용자(users.id)")
        private Long userId;

        @Schema(description = "[승인된 프로젝트] 멤버십이 ACTIVE 상태인 프로젝트 신청 목록")
        private List<EnrollmentResponse> activeProjects;

        @Schema(description = "[신청 대기 프로젝트] 리더 승인을 기다리는 PENDING 상태 신청 목록")
        private List<EnrollmentResponse> pendingProjects;

        @Schema(description = "[회수된 프로젝트] 거절되었거나 접근이 회수된 CANCELLED 상태 신청 목록 (재신청 가능)")
        private List<EnrollmentResponse> cancelledProjects;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrollmentHistoryResponse {
        private Long userId;
        private List<Long> activeProjectIds;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message("성공")
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
