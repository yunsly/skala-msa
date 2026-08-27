package com.lecture.course.service;

import com.lecture.course.client.EnrollmentServiceClient;
import com.lecture.course.dto.CourseDto;
import com.lecture.course.dto.ProjectDto;
import com.lecture.course.entity.Course;
import com.lecture.course.entity.Project;
import com.lecture.course.repository.CourseRepository;
import com.lecture.course.repository.ProjectRepository;
import com.lecture.course.security.AuthenticatedActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private EnrollmentServiceClient enrollmentServiceClient;

    private CourseService courseService;

    @BeforeEach
    void setUp() {
        courseService = new CourseService(
                courseRepository,
                projectRepository,
                enrollmentServiceClient
        );
    }

    @Test
    void memberSeesOnlyActiveMembershipAssetsWithFilters() {
        AuthenticatedActor member = actor(10L, AuthenticatedActor.Role.MEMBER);
        Project allowed = project(1L, 20L);
        Project denied = project(2L, 30L);
        Course matching = course(1L, 1L, "OpenAI Production Key", Course.Category.API_KEY);
        Course wrongCategory = course(2L, 1L, "OpenAI Team Plan", Course.Category.SUBSCRIPTION_PLAN);
        Course otherProject = course(3L, 2L, "OpenAI Other Key", Course.Category.API_KEY);

        when(projectRepository.findByStatus(Project.Status.ACTIVE))
                .thenReturn(List.of(allowed, denied));
        when(enrollmentServiceClient.getActiveProjectIds(10L)).thenReturn(List.of(1L));
        when(courseRepository.findByStatus(Course.Status.ACTIVE))
                .thenReturn(List.of(matching, wrongCategory, otherProject));
        when(enrollmentServiceClient.countActiveMembers(1L)).thenReturn(3L);

        List<CourseDto.CourseResponse> result = courseService.getCourses(
                null,
                Course.Category.API_KEY,
                "production",
                member
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(1L);
        assertThat(result.getFirst().getActiveMemberCount()).isEqualTo(3L);
        assertThat(result.getFirst().getManagerId()).isEqualTo(20L);
    }

    @Test
    void adminSeesAllActiveProjectsWithoutMembershipLookup() {
        AuthenticatedActor admin = actor(1L, AuthenticatedActor.Role.ADMIN);
        when(projectRepository.findByStatus(Project.Status.ACTIVE))
                .thenReturn(List.of(project(1L, 20L), project(2L, 30L)));
        when(enrollmentServiceClient.countActiveMembers(any(Long.class))).thenReturn(2L);

        List<ProjectDto.ProjectResponse> result = courseService.getProjects(admin);

        assertThat(result).extracting(ProjectDto.ProjectResponse::getId)
                .containsExactly(1L, 2L);
        verify(enrollmentServiceClient, never()).getActiveProjectIds(any(Long.class));
    }

    @Test
    void leaderCreatesProjectAsOwner() {
        AuthenticatedActor leader = actor(20L, AuthenticatedActor.Role.LEADER);
        ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                .name("Credential Platform")
                .description("플랫폼 프로젝트")
                .build();
        when(projectRepository.existsByName("Credential Platform")).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollmentServiceClient.countActiveMembers(null)).thenReturn(0L);

        ProjectDto.ProjectResponse response = courseService.createProject(request, leader);

        assertThat(response.getOwnerId()).isEqualTo(20L);
        assertThat(response.getStatus()).isEqualTo(Project.Status.ACTIVE);
    }

    @Test
    void memberCannotCreateProject() {
        ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                .name("Denied Project")
                .build();

        assertThatThrownBy(() -> courseService.createProject(
                request,
                actor(10L, AuthenticatedActor.Role.MEMBER)
        )).isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void leaderRegistersAssetOnlyToOwnedProject() {
        CourseDto.CreateRequest request = apiKeyRequest(1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, 20L)));
        when(courseRepository.existsByProjectIdAndTitle(1L, request.getTitle())).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollmentServiceClient.countActiveMembers(1L)).thenReturn(4L);

        CourseDto.CourseResponse response = courseService.createCourse(
                request,
                actor(20L, AuthenticatedActor.Role.LEADER)
        );

        assertThat(response.getManagerId()).isEqualTo(20L);
        assertThat(response.getProvider()).isEqualTo("OpenAI");
        assertThat(response.getActiveMemberCount()).isEqualTo(4L);

        CourseDto.CreateRequest deniedRequest = apiKeyRequest(2L);
        when(projectRepository.findById(2L)).thenReturn(Optional.of(project(2L, 30L)));

        assertThatThrownBy(() -> courseService.createCourse(
                deniedRequest,
                actor(20L, AuthenticatedActor.Role.LEADER)
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void detailReturnsDecryptedMetadataOnlyAfterAccessCheck() {
        Course asset = course(1L, 1L, "Demo Key", Course.Category.API_KEY);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, 20L)));
        when(enrollmentServiceClient.getActiveProjectIds(10L)).thenReturn(List.of(1L));
        when(enrollmentServiceClient.countActiveMembers(1L)).thenReturn(2L);

        CourseDto.CourseDetailResponse response = courseService.getCourse(
                1L,
                actor(10L, AuthenticatedActor.Role.MEMBER)
        );

        assertThat(response.getSecretValue()).isEqualTo("demo-secret");
        assertThat(response.getActiveMemberCount()).isEqualTo(2L);
    }

    @Test
    void leaderUpdatesRotatesAndRevokesOwnedAsset() {
        Course asset = course(1L, 1L, "Demo Key", Course.Category.API_KEY);
        AuthenticatedActor leader = actor(20L, AuthenticatedActor.Role.LEADER);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, 20L)));
        when(courseRepository.existsByProjectIdAndTitleAndIdNot(1L, "Production Key", 1L))
                .thenReturn(false);
        when(enrollmentServiceClient.countActiveMembers(1L)).thenReturn(2L);

        CourseDto.CourseResponse updated = courseService.updateCourse(
                1L,
                CourseDto.UpdateRequest.builder()
                        .title("Production Key")
                        .provider("Anthropic")
                        .build(),
                leader
        );
        CourseDto.CourseDetailResponse rotated = courseService.rotateCourseSecret(
                1L,
                CourseDto.RotateSecretRequest.builder()
                        .secretValue("rotated-secret")
                        .build(),
                leader
        );
        courseService.revokeCourse(1L, leader);

        assertThat(updated.getTitle()).isEqualTo("Production Key");
        assertThat(updated.getProvider()).isEqualTo("Anthropic");
        assertThat(rotated.getSecretValue()).isEqualTo("rotated-secret");
        assertThat(rotated.getLastRotatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(asset.getStatus()).isEqualTo(Course.Status.REVOKED);
    }

    private AuthenticatedActor actor(Long userId, AuthenticatedActor.Role role) {
        return new AuthenticatedActor(userId, role);
    }

    private Project project(Long id, Long ownerId) {
        return Project.builder()
                .id(id)
                .name("Project " + id)
                .ownerId(ownerId)
                .status(Project.Status.ACTIVE)
                .build();
    }

    private Course course(Long id, Long projectId, String title, Course.Category category) {
        return Course.builder()
                .id(id)
                .projectId(projectId)
                .title(title)
                .description("Production credential")
                .category(category)
                .provider("OpenAI")
                .planName(category == Course.Category.SUBSCRIPTION_PLAN ? "Team" : null)
                .instructorId(20L)
                .metadata(category == Course.Category.API_KEY ? "demo-secret" : null)
                .status(Course.Status.ACTIVE)
                .build();
    }

    private CourseDto.CreateRequest apiKeyRequest(Long projectId) {
        return CourseDto.CreateRequest.builder()
                .projectId(projectId)
                .title("OpenAI Demo Key")
                .category(Course.Category.API_KEY)
                .provider("OpenAI")
                .secretValue("demo-secret")
                .build();
    }
}
