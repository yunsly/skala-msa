package com.lecture.course.controller;

import com.lecture.course.config.SecurityConfig;
import com.lecture.course.dto.CourseDto;
import com.lecture.course.security.AuthenticatedActor;
import com.lecture.course.security.AuthenticatedActorResolver;
import com.lecture.course.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
@Import(SecurityConfig.class)
class CourseControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private AuthenticatedActorResolver actorResolver;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void assetListRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalApiRequiresAndAcceptsServiceScope() throws Exception {
        mockMvc.perform(get("/api/courses/internal/projects/1/exists").with(jwt()))
                .andExpect(status().isForbidden());

        when(courseService.existsProject(1L)).thenReturn(true);

        mockMvc.perform(get("/api/courses/internal/projects/1/exists")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("SCOPE_service.read")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    void detailUsesNoStoreAndReturnsSecretAfterAuthorization() throws Exception {
        AuthenticatedActor member = new AuthenticatedActor(7L, AuthenticatedActor.Role.MEMBER);
        when(actorResolver.resolve(any(Jwt.class), eq(7L))).thenReturn(member);
        when(courseService.getCourse(1L, member)).thenReturn(
                CourseDto.CourseDetailResponse.builder()
                        .id(1L)
                        .projectId(2L)
                        .secretValue("demo-secret")
                        .build()
        );

        mockMvc.perform(get("/api/courses/1")
                        .with(jwt().jwt(token -> token
                                .subject("7")
                                .claim("user_id", 7L)))
                        .header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.data.secretValue").value("demo-secret"));
    }

}
