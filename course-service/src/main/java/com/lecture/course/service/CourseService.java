package com.lecture.course.service;

import com.lecture.course.client.EnrollmentServiceClient;
import com.lecture.course.dto.CourseDto;
import com.lecture.course.dto.ProjectDto;
import com.lecture.course.entity.Course;
import com.lecture.course.entity.Project;
import com.lecture.course.repository.CourseRepository;
import com.lecture.course.repository.ProjectRepository;
import com.lecture.course.security.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final ProjectRepository projectRepository;
    private final EnrollmentServiceClient enrollmentServiceClient;

    @Transactional
    public ProjectDto.ProjectResponse createProject(
            ProjectDto.CreateRequest request,
            AuthenticatedActor actor
    ) {
        requireProjectCreator(actor);
        if (projectRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("이미 존재하는 프로젝트명입니다: " + request.getName());
        }

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(actor.userId())
                .build();
        return toProjectResponse(projectRepository.save(project));
    }

    @Transactional
    public ProjectDto.ProjectResponse updateProject(
            Long projectId,
            ProjectDto.UpdateRequest request,
            AuthenticatedActor actor
    ) {
        Project project = findProjectById(projectId);
        requireProjectManager(actor, project);
        if (request.getName() == null
                && request.getDescription() == null
                && request.getStatus() == null) {
            throw new IllegalArgumentException("수정할 프로젝트 정보가 없습니다.");
        }
        if (request.getName() != null
                && projectRepository.existsByNameAndIdNot(request.getName(), projectId)) {
            throw new IllegalArgumentException("이미 존재하는 프로젝트명입니다: " + request.getName());
        }
        project.update(request.getName(), request.getDescription(), request.getStatus());
        return toProjectResponse(project);
    }

    public List<ProjectDto.ProjectResponse> getProjects(AuthenticatedActor actor) {
        Set<Long> activeMemberships = activeMemberships(actor);
        return projectRepository.findByStatus(Project.Status.ACTIVE).stream()
                .filter(project -> canViewProject(actor, project, activeMemberships))
                .map(this::toProjectResponse)
                .toList();
    }

    public ProjectDto.ProjectResponse getProject(Long projectId, AuthenticatedActor actor) {
        Project project = findProjectById(projectId);
        requireProjectAccess(actor, project, activeMemberships(actor));
        return toProjectResponse(project);
    }

    public boolean existsProject(Long projectId) {
        return projectRepository.existsById(projectId);
    }

    @Transactional
    public CourseDto.CourseResponse createCourse(
            CourseDto.CreateRequest request,
            AuthenticatedActor actor
    ) {
        Project project = findProjectById(request.getProjectId());
        requireProjectManager(actor, project);
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
                .instructorId(actor.userId())
                .expiresAt(request.getExpiresAt())
                .renewalAt(request.getRenewalAt())
                .lastRotatedAt(request.getLastRotatedAt())
                .metadata(request.getSecretValue())
                .build();

        return toCourseResponse(courseRepository.save(course), activeMemberCount(project.getId()));
    }

    public CourseDto.CourseDetailResponse getCourse(Long id, AuthenticatedActor actor) {
        Course course = findCourseById(id);
        Project project = findProjectById(course.getProjectId());
        requireProjectAccess(actor, project, activeMemberships(actor));
        return CourseDto.CourseDetailResponse.from(course, activeMemberCount(project.getId()));
    }

    @Transactional
    public CourseDto.CourseResponse updateCourse(
            Long id,
            CourseDto.UpdateRequest request,
            AuthenticatedActor actor
    ) {
        Course course = findCourseById(id);
        Project project = findProjectById(course.getProjectId());
        requireProjectManager(actor, project);
        validateUpdateRequest(course, request);

        if (request.getTitle() != null
                && courseRepository.existsByProjectIdAndTitleAndIdNot(
                course.getProjectId(),
                request.getTitle(),
                id
        )) {
            throw new IllegalArgumentException("프로젝트에 동일한 자산명이 이미 존재합니다.");
        }

        course.updateDetails(
                request.getTitle(),
                request.getDescription(),
                request.getProvider(),
                request.getPlanName(),
                request.getExpiresAt(),
                request.getRenewalAt()
        );
        return toCourseResponse(course, activeMemberCount(project.getId()));
    }

    @Transactional
    public CourseDto.CourseDetailResponse rotateCourseSecret(
            Long id,
            CourseDto.RotateSecretRequest request,
            AuthenticatedActor actor
    ) {
        Course course = findCourseById(id);
        Project project = findProjectById(course.getProjectId());
        requireProjectManager(actor, project);
        if (course.getCategory() != Course.Category.API_KEY) {
            throw new IllegalArgumentException("API_KEY 유형만 Secret을 회전할 수 있습니다.");
        }

        course.rotateSecret(request.getSecretValue(), LocalDateTime.now());
        return CourseDto.CourseDetailResponse.from(course, activeMemberCount(project.getId()));
    }

    @Transactional
    public void revokeCourse(Long id, AuthenticatedActor actor) {
        Course course = findCourseById(id);
        Project project = findProjectById(course.getProjectId());
        requireProjectManager(actor, project);
        course.revoke();
    }

    public CourseDto.CourseResponse getCourseInternal(Long id) {
        Course course = findCourseById(id);
        return toCourseResponse(course, activeMemberCount(course.getProjectId()));
    }

    public List<CourseDto.CourseResponse> getCourses(
            Long projectId,
            Course.Category category,
            String keyword,
            AuthenticatedActor actor
    ) {
        Set<Long> visibleProjectIds = visibleProjectIds(actor);
        if (projectId != null && !visibleProjectIds.contains(projectId)) {
            throw new AccessDeniedException("프로젝트 자산을 조회할 권한이 없습니다.");
        }

        List<Course> courses = projectId == null
                ? courseRepository.findByStatus(Course.Status.ACTIVE)
                : courseRepository.findByProjectIdAndStatus(projectId, Course.Status.ACTIVE);

        Map<Long, Long> activeMemberCounts = new HashMap<>();
        return courses.stream()
                .filter(course -> visibleProjectIds.contains(course.getProjectId()))
                .filter(course -> category == null || course.getCategory() == category)
                .filter(course -> matchesKeyword(course, keyword))
                .map(course -> toCourseResponse(
                        course,
                        activeMemberCounts.computeIfAbsent(
                                course.getProjectId(),
                                this::activeMemberCount
                        )
                ))
                .toList();
    }

    public List<CourseDto.CourseResponse> getCoursesByCategory(
            Long projectId,
            Course.Category category,
            AuthenticatedActor actor
    ) {
        return getCourses(projectId, category, null, actor);
    }

    public boolean existsCourse(Long id) {
        return courseRepository.existsById(id);
    }

    private Set<Long> visibleProjectIds(AuthenticatedActor actor) {
        List<Project> activeProjects = projectRepository.findByStatus(Project.Status.ACTIVE);
        if (actor.role() == AuthenticatedActor.Role.ADMIN) {
            return activeProjects.stream().map(Project::getId).collect(Collectors.toSet());
        }

        Set<Long> visibleIds = new HashSet<>(activeMemberships(actor));
        if (actor.role() == AuthenticatedActor.Role.LEADER) {
            activeProjects.stream()
                    .filter(project -> project.getOwnerId().equals(actor.userId()))
                    .map(Project::getId)
                    .forEach(visibleIds::add);
        }
        return visibleIds;
    }

    private Set<Long> activeMemberships(AuthenticatedActor actor) {
        if (actor.role() == AuthenticatedActor.Role.ADMIN) {
            return Set.of();
        }
        return new HashSet<>(enrollmentServiceClient.getActiveProjectIds(actor.userId()));
    }

    private boolean canViewProject(
            AuthenticatedActor actor,
            Project project,
            Set<Long> activeMemberships
    ) {
        return actor.role() == AuthenticatedActor.Role.ADMIN
                || project.getOwnerId().equals(actor.userId())
                || activeMemberships.contains(project.getId());
    }

    private void requireProjectAccess(
            AuthenticatedActor actor,
            Project project,
            Set<Long> activeMemberships
    ) {
        if (!canViewProject(actor, project, activeMemberships)) {
            throw new AccessDeniedException("프로젝트를 조회할 권한이 없습니다.");
        }
    }

    private void requireProjectCreator(AuthenticatedActor actor) {
        if (actor.role() != AuthenticatedActor.Role.LEADER
                && actor.role() != AuthenticatedActor.Role.ADMIN) {
            throw new AccessDeniedException("LEADER 또는 ADMIN만 프로젝트를 생성할 수 있습니다.");
        }
    }

    private void requireProjectManager(AuthenticatedActor actor, Project project) {
        if (actor.role() == AuthenticatedActor.Role.ADMIN) {
            return;
        }
        if (actor.role() != AuthenticatedActor.Role.LEADER
                || !project.getOwnerId().equals(actor.userId())) {
            throw new AccessDeniedException("프로젝트 리더 또는 ADMIN만 관리할 수 있습니다.");
        }
    }

    private boolean matchesKeyword(Course course, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(course.getTitle(), normalized)
                || contains(course.getDescription(), normalized)
                || contains(course.getProvider(), normalized)
                || contains(course.getPlanName(), normalized);
    }

    private boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
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

    private void validateUpdateRequest(Course course, CourseDto.UpdateRequest request) {
        if (request.getTitle() == null
                && request.getDescription() == null
                && request.getProvider() == null
                && request.getPlanName() == null
                && request.getExpiresAt() == null
                && request.getRenewalAt() == null) {
            throw new IllegalArgumentException("수정할 자산 정보가 없습니다.");
        }
        if (request.getTitle() != null && request.getTitle().isBlank()) {
            throw new IllegalArgumentException("자산명은 비워둘 수 없습니다.");
        }
        if (request.getProvider() != null && request.getProvider().isBlank()) {
            throw new IllegalArgumentException("제공자는 비워둘 수 없습니다.");
        }
        if (course.getCategory() == Course.Category.API_KEY && request.getPlanName() != null) {
            throw new IllegalArgumentException("API_KEY에는 planName을 사용할 수 없습니다.");
        }
        if (course.getCategory() == Course.Category.SUBSCRIPTION_PLAN
                && request.getPlanName() != null
                && request.getPlanName().isBlank()) {
            throw new IllegalArgumentException("SUBSCRIPTION_PLAN의 planName은 비워둘 수 없습니다.");
        }
    }

    private ProjectDto.ProjectResponse toProjectResponse(Project project) {
        return ProjectDto.ProjectResponse.from(project, activeMemberCount(project.getId()));
    }

    private CourseDto.CourseResponse toCourseResponse(Course course, long activeMemberCount) {
        return CourseDto.CourseResponse.from(course, activeMemberCount);
    }

    private long activeMemberCount(Long projectId) {
        return enrollmentServiceClient.countActiveMembers(projectId);
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
