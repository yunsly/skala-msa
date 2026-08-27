package com.lecture.course.dto;

import com.lecture.course.entity.Course;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

public class CourseDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotNull(message = "프로젝트 ID는 필수입니다")
        private Long projectId;

        @NotBlank(message = "자산명은 필수입니다")
        private String title;

        private String description;

        @NotNull(message = "Credential 유형은 필수입니다")
        private Course.Category category;

        @NotBlank(message = "제공자는 필수입니다")
        private String provider;

        private String planName;
        private String secretValue;
        private LocalDateTime expiresAt;
        private LocalDateTime renewalAt;
        private LocalDateTime lastRotatedAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CourseResponse {
        private Long id;
        private Long projectId;
        private String title;
        private String description;
        private Course.Category category;
        private String provider;
        private String planName;
        private Long instructorId;
        private LocalDateTime expiresAt;
        private LocalDateTime renewalAt;
        private LocalDateTime lastRotatedAt;
        private Course.Status status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static CourseResponse from(Course course) {
            return CourseResponse.builder()
                    .id(course.getId())
                    .projectId(course.getProjectId())
                    .title(course.getTitle())
                    .description(course.getDescription())
                    .category(course.getCategory())
                    .provider(course.getProvider())
                    .planName(course.getPlanName())
                    .instructorId(course.getInstructorId())
                    .expiresAt(course.getExpiresAt())
                    .renewalAt(course.getRenewalAt())
                    .lastRotatedAt(course.getLastRotatedAt())
                    .status(course.getStatus())
                    .createdAt(course.getCreatedAt())
                    .updatedAt(course.getUpdatedAt())
                    .build();
        }
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
