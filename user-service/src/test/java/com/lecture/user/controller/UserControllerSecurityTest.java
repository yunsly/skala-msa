package com.lecture.user.controller;

import com.lecture.user.config.SecurityConfig;
import com.lecture.user.dto.UserDto;
import com.lecture.user.entity.User;
import com.lecture.user.security.AuthenticatedUser;
import com.lecture.user.security.AuthenticatedUserResolver;
import com.lecture.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticatedUserResolver authenticatedUserResolver;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void registerIsPublicAndAcceptsMember() throws Exception {
        when(userService.register(any(UserDto.RegisterRequest.class)))
                .thenReturn(userResponse(1L, User.Role.MEMBER));

        mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "password": "password123",
                                  "name": "멤버",
                                  "role": "MEMBER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
    }

    @Test
    void legacyRoleReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "legacy@example.com",
                                  "password": "password123",
                                  "name": "레거시",
                                  "role": "STUDENT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void missingRoleReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "password": "password123",
                                  "name": "멤버"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void meRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meUsesVerifiedJwtAndGatewayHeaders() throws Exception {
        when(authenticatedUserResolver.resolve(any(Jwt.class), eq(7L), eq("STUDENT")))
                .thenReturn(new AuthenticatedUser(7L));
        when(userService.getCurrentUser(7L))
                .thenReturn(userResponse(7L, User.Role.MEMBER));

        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(token -> token
                                .subject("7")
                                .claim("user_id", 7L)
                                .claim("role", "STUDENT")))
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7L))
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
    }

    @Test
    void internalUserApiRequiresServiceScope() throws Exception {
        mockMvc.perform(get("/api/users/internal/1").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadUserById() throws Exception {
        when(authenticatedUserResolver.resolve(any(Jwt.class), eq(10L), eq("INSTRUCTOR")))
                .thenReturn(new AuthenticatedUser(10L));
        when(userService.getUserByIdAsAdmin(10L, 1L))
                .thenReturn(userResponse(1L, User.Role.MEMBER));

        mockMvc.perform(get("/api/users/1")
                        .with(jwt().jwt(token -> token
                                .subject("10")
                                .claim("user_id", 10L)
                                .claim("role", "INSTRUCTOR")))
                        .header("X-User-Id", "10")
                        .header("X-User-Role", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void userByIdRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isUnauthorized());
    }

    private UserDto.UserResponse userResponse(Long id, User.Role role) {
        return UserDto.UserResponse.builder()
                .id(id)
                .email("member@example.com")
                .name("사용자")
                .role(role)
                .build();
    }
}
