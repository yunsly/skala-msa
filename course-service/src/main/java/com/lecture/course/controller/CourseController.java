package com.lecture.course.controller;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.dto.ProjectDto;
import com.lecture.course.entity.Course;
import com.lecture.course.security.AuthenticatedActor;
import com.lecture.course.security.AuthenticatedActorResolver;
import com.lecture.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final AuthenticatedActorResolver actorResolver;

    @PostMapping("/projects")
    public ResponseEntity<CourseDto.ApiResponse<ProjectDto.ProjectResponse>> createProject(
            @Valid @RequestBody ProjectDto.CreateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CourseDto.ApiResponse.success(
                        courseService.createProject(request, actor(jwt, gatewayUserId))
                ));
    }

    @PatchMapping("/projects/{projectId}")
    public ResponseEntity<CourseDto.ApiResponse<ProjectDto.ProjectResponse>> updateProject(
            @PathVariable Long projectId,
            @RequestBody ProjectDto.UpdateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        return ResponseEntity.ok(CourseDto.ApiResponse.success(
                courseService.updateProject(projectId, request, actor(jwt, gatewayUserId))
        ));
    }

    @GetMapping("/projects")
    public ResponseEntity<CourseDto.ApiResponse<List<ProjectDto.ProjectResponse>>> getProjects(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        return ResponseEntity.ok(CourseDto.ApiResponse.success(
                courseService.getProjects(actor(jwt, gatewayUserId))
        ));
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<CourseDto.ApiResponse<ProjectDto.ProjectResponse>> getProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        return ResponseEntity.ok(CourseDto.ApiResponse.success(
                courseService.getProject(projectId, actor(jwt, gatewayUserId))
        ));
    }

    @PostMapping
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> createCourse(
            @Valid @RequestBody CourseDto.CreateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CourseDto.ApiResponse.success(
                        courseService.createCourse(request, actor(jwt, gatewayUserId))
                ));
    }

    @GetMapping
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getCourses(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Course.Category category,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        return ResponseEntity.ok(CourseDto.ApiResponse.success(
                courseService.getCourses(
                        projectId,
                        category,
                        keyword,
                        actor(jwt, gatewayUserId)
                )
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseDetailResponse>> getCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(CourseDto.ApiResponse.success(
                        courseService.getCourse(id, actor(jwt, gatewayUserId))
                ));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> updateCourse(
            @PathVariable Long id,
            @RequestBody CourseDto.UpdateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        return ResponseEntity.ok(CourseDto.ApiResponse.success(
                courseService.updateCourse(id, request, actor(jwt, gatewayUserId))
        ));
    }

    @PatchMapping("/{id}/rotate")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseDetailResponse>> rotateCourseSecret(
            @PathVariable Long id,
            @Valid @RequestBody CourseDto.RotateSecretRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(CourseDto.ApiResponse.success(
                        courseService.rotateCourseSecret(id, request, actor(jwt, gatewayUserId))
                ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        courseService.revokeCourse(id, actor(jwt, gatewayUserId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getByCategory(
            @PathVariable Course.Category category,
            @RequestParam(required = false) Long projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId
    ) {
        return ResponseEntity.ok(CourseDto.ApiResponse.success(
                courseService.getCoursesByCategory(
                        projectId,
                        category,
                        actor(jwt, gatewayUserId)
                )
        ));
    }

    @GetMapping("/internal/projects/{projectId}/exists")
    public ResponseEntity<Boolean> existsProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(courseService.existsProject(projectId));
    }

    @GetMapping("/internal/exists/{id}")
    public ResponseEntity<Boolean> existsCourse(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.existsCourse(id));
    }

    @GetMapping("/internal/{id}")
    public ResponseEntity<CourseDto.CourseResponse> getCourseInternal(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseInternal(id));
    }

    private AuthenticatedActor actor(Jwt jwt, Long gatewayUserId) {
        return actorResolver.resolve(jwt, gatewayUserId);
    }
}
