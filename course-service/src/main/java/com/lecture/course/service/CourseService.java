package com.lecture.course.service;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.dto.ProjectDto;
import com.lecture.course.entity.Course;
import com.lecture.course.entity.Project;
import com.lecture.course.repository.CourseRepository;
import com.lecture.course.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public ProjectDto.ProjectResponse createProject(
            ProjectDto.CreateRequest request,
            Long ownerId
    ) {
        if (projectRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("이미 존재하는 프로젝트명입니다: " + request.getName());
        }

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(ownerId)
                .build();
        return ProjectDto.ProjectResponse.from(projectRepository.save(project));
    }

    public List<ProjectDto.ProjectResponse> getProjects() {
        return projectRepository.findByStatus(Project.Status.ACTIVE).stream()
                .map(ProjectDto.ProjectResponse::from)
                .toList();
    }

    public ProjectDto.ProjectResponse getProject(Long projectId) {
        return ProjectDto.ProjectResponse.from(findProjectById(projectId));
    }

    public boolean existsProject(Long projectId) {
        return projectRepository.existsById(projectId);
    }

    @Transactional
    public CourseDto.CourseResponse createCourse(
            CourseDto.CreateRequest request,
            Long instructorId
    ) {
        Project project = findProjectById(request.getProjectId());
        if (project.getStatus() != Project.Status.ACTIVE) {
            throw new IllegalStateException("ACTIVE 프로젝트에만 자산을 등록할 수 있습니다.");
        }
        if (courseRepository.existsByProjectIdAndTitle(
                request.getProjectId(),
                request.getTitle()
        )) {
            throw new IllegalArgumentException("프로젝트에 동일한 자산명이 이미 존재합니다.");
        }

        validateTypeSpecificFields(request);

        Course course = Course.builder()
                .projectId(request.getProjectId())
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .provider(request.getProvider())
                .planName(request.getPlanName())
                .instructorId(instructorId)
                .expiresAt(request.getExpiresAt())
                .renewalAt(request.getRenewalAt())
                .lastRotatedAt(request.getLastRotatedAt())
                .metadata(request.getSecretValue())
                .build();

        return CourseDto.CourseResponse.from(courseRepository.save(course));
    }

    public CourseDto.CourseResponse getCourse(Long id) {
        return CourseDto.CourseResponse.from(findCourseById(id));
    }

    public List<CourseDto.CourseResponse> getCourses(Long projectId) {
        List<Course> courses = projectId == null
                ? courseRepository.findByStatus(Course.Status.ACTIVE)
                : courseRepository.findByProjectIdAndStatus(projectId, Course.Status.ACTIVE);
        return courses.stream().map(CourseDto.CourseResponse::from).toList();
    }

    public List<CourseDto.CourseResponse> getCoursesByCategory(
            Long projectId,
            Course.Category category
    ) {
        List<Course> courses = projectId == null
                ? courseRepository.findByCategoryAndStatus(category, Course.Status.ACTIVE)
                : courseRepository.findByProjectIdAndCategoryAndStatus(
                        projectId,
                        category,
                        Course.Status.ACTIVE
                );
        return courses.stream().map(CourseDto.CourseResponse::from).toList();
    }

    public boolean existsCourse(Long id) {
        return courseRepository.existsById(id);
    }

    private void validateTypeSpecificFields(CourseDto.CreateRequest request) {
        if (request.getCategory() == Course.Category.API_KEY) {
            if (request.getSecretValue() == null || request.getSecretValue().isBlank()) {
                throw new IllegalArgumentException("API_KEY에는 secretValue가 필요합니다.");
            }
            if (request.getPlanName() != null) {
                throw new IllegalArgumentException("API_KEY에는 planName을 사용할 수 없습니다.");
            }
            return;
        }

        if (request.getPlanName() == null || request.getPlanName().isBlank()) {
            throw new IllegalArgumentException("SUBSCRIPTION_PLAN에는 planName이 필요합니다.");
        }
        if (request.getSecretValue() != null && !request.getSecretValue().isBlank()) {
            throw new IllegalArgumentException("SUBSCRIPTION_PLAN에는 secretValue를 저장하지 않습니다.");
        }
    }

    private Project findProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다: " + id));
    }

    private Course findCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("자산을 찾을 수 없습니다: " + id));
    }
}
