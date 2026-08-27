package com.lecture.enrollment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.service.EnrollmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnrollmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class EnrollmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EnrollmentService enrollmentService;

    private EnrollmentDto.MyProjectsResponse myProjectsOf(Long userId, Long activeProjectId) {
        return EnrollmentDto.MyProjectsResponse.builder()
                .userId(userId)
                .activeProjects(List.of(EnrollmentDto.EnrollmentResponse.builder()
                        .id(userId * 10)
                        .userId(userId)
                        .projectId(activeProjectId)
                        .status(Enrollment.Status.ACTIVE)
                        .build()))
                .pendingProjects(List.of())
                .build();
    }

    @Test
    void myProjects_derivesUserFromHeader_notFromClientInput() throws Exception {
        when(enrollmentService.getMyProjects(7L)).thenReturn(myProjectsOf(7L, 100L));

        mockMvc.perform(get("/api/enrollments/my-projects").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.activeProjects[0].userId").value(7));

        verify(enrollmentService).getMyProjects(7L);
    }

    @Test
    void myProjects_requiresUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/enrollments/my-projects"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myProjects_isolatesUsersByHeader() throws Exception {
        when(enrollmentService.getMyProjects(1L)).thenReturn(myProjectsOf(1L, 11L));
        when(enrollmentService.getMyProjects(2L)).thenReturn(myProjectsOf(2L, 22L));

        mockMvc.perform(get("/api/enrollments/my-projects").header("X-User-Id", "1"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.activeProjects[0].projectId").value(11));

        mockMvc.perform(get("/api/enrollments/my-projects").header("X-User-Id", "2"))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.activeProjects[0].projectId").value(22));
    }

    @Test
    void enroll_derivesApplicantFromHeader_notFromRequestBody() throws Exception {
        when(enrollmentService.enroll(eq(5L), eq(10L), eq("결제 모듈 개발")))
                .thenReturn(EnrollmentDto.EnrollmentResponse.builder()
                        .id(1L)
                        .userId(5L)
                        .projectId(10L)
                        .status(Enrollment.Status.PENDING)
                        .build());

        String body = objectMapper.writeValueAsString(
                new EnrollmentDto.EnrollRequest(10L, "결제 모듈 개발"));

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", "5")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(5));

        verify(enrollmentService).enroll(5L, 10L, "결제 모듈 개발");
    }

    @Test
    void enroll_requiresUserIdHeader() throws Exception {
        String body = objectMapper.writeValueAsString(
                new EnrollmentDto.EnrollRequest(10L, "사유"));

        mockMvc.perform(post("/api/enrollments")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
