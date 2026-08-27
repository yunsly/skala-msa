package com.lecture.course.dto;

import com.lecture.course.entity.Project;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

public class ProjectDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank(message = "프로젝트명은 필수입니다")
        private String name;
        private String description;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String name;
        private String description;
        private Project.Status status;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProjectResponse {
        private Long id;
        private String name;
        private String description;
        private Long ownerId;
        private Project.Status status;
        private long activeMemberCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static ProjectResponse from(Project project, long activeMemberCount) {
            return ProjectResponse.builder()
                    .id(project.getId())
                    .name(project.getName())
                    .description(project.getDescription())
                    .ownerId(project.getOwnerId())
                    .status(project.getStatus())
                    .activeMemberCount(activeMemberCount)
                    .createdAt(project.getCreatedAt())
                    .updatedAt(project.getUpdatedAt())
                    .build();
        }
    }
}
