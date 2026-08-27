package com.lecture.course.controller;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.dto.ProjectDto;
import com.lecture.course.entity.Course;
import com.lecture.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping("/projects")
    public ResponseEntity<CourseDto.ApiResponse<ProjectDto.ProjectResponse>> createProject(
            @Valid @RequestBody ProjectDto.CreateRequest request,
            @RequestHeader("X-User-Id") Long ownerId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CourseDto.ApiResponse.success(
                        courseService.createProject(request, ownerId)
                ));
    }

    @GetMapping("/projects")
    public ResponseEntity<CourseDto.ApiResponse<List<ProjectDto.ProjectResponse>>> getProjects() {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getProjects())
        );
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<CourseDto.ApiResponse<ProjectDto.ProjectResponse>> getProject(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getProject(projectId))
        );
    }

    @PostMapping
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> createCourse(
            @Valid @RequestBody CourseDto.CreateRequest request,
            @RequestHeader("X-User-Id") Long instructorId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CourseDto.ApiResponse.success(
                        courseService.createCourse(request, instructorId)
                ));
    }

    @GetMapping
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getCourses(
            @RequestParam(required = false) Long projectId
    ) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getCourses(projectId))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> getCourse(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getCourse(id))
        );
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getByCategory(
            @PathVariable Course.Category category,
            @RequestParam(required = false) Long projectId
    ) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(
                        courseService.getCoursesByCategory(projectId, category)
                )
        );
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
        return ResponseEntity.ok(courseService.getCourse(id));
    }
}
