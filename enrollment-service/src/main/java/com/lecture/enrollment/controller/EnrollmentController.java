package com.lecture.enrollment.controller;

import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    public ResponseEntity<EnrollmentDto.ApiResponse<EnrollmentDto.EnrollmentResponse>> enroll(
            @Valid @RequestBody EnrollmentDto.EnrollRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) {
        EnrollmentDto.EnrollmentResponse response = enrollmentService.enroll(
                userId,
                request.getProjectId(),
                request.getReason()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EnrollmentDto.ApiResponse.success(response));
    }

    @GetMapping("/my")
    public ResponseEntity<EnrollmentDto.ApiResponse<List<EnrollmentDto.EnrollmentResponse>>> getMine(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(
                EnrollmentDto.ApiResponse.success(
                        enrollmentService.getEnrollmentsByUser(userId)
                )
        );
    }

    @GetMapping("/my-projects")
    public ResponseEntity<EnrollmentDto.ApiResponse<EnrollmentDto.MyProjectsResponse>> getMyProjects(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(
                EnrollmentDto.ApiResponse.success(
                        enrollmentService.getMyProjects(userId)
                )
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<EnrollmentDto.ApiResponse<List<EnrollmentDto.EnrollmentResponse>>> getByUser(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                EnrollmentDto.ApiResponse.success(
                        enrollmentService.getEnrollmentsByUser(userId)
                )
        );
    }

    @GetMapping("/internal/{enrollmentId}")
    public ResponseEntity<EnrollmentDto.EnrollmentResponse> getEnrollmentInternal(
            @PathVariable Long enrollmentId
    ) {
        return ResponseEntity.ok(enrollmentService.getEnrollment(enrollmentId));
    }

    @GetMapping("/internal/history/{userId}")
    public ResponseEntity<EnrollmentDto.EnrollmentHistoryResponse> getEnrollmentHistory(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(enrollmentService.getEnrollmentHistory(userId));
    }

    @GetMapping("/internal/projects/{projectId}/active-count")
    public ResponseEntity<Long> countActiveMembers(@PathVariable Long projectId) {
        return ResponseEntity.ok(enrollmentService.countActiveMembers(projectId));
    }

    @PatchMapping("/internal/{userId}/{projectId}/access")
    public ResponseEntity<Void> markAccessed(
            @PathVariable Long userId,
            @PathVariable Long projectId
    ) {
        enrollmentService.markAccessed(userId, projectId);
        return ResponseEntity.noContent().build();
    }
}
