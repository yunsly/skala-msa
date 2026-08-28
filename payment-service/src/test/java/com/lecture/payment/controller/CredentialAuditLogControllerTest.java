package com.lecture.payment.controller;

import com.lecture.payment.config.SecurityConfig;
import com.lecture.payment.dto.CredentialAuditLogDto;
import com.lecture.payment.entity.CredentialAuditLog;
import com.lecture.payment.service.CredentialAuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CredentialAuditLogController.class)
@Import(SecurityConfig.class)
class CredentialAuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CredentialAuditLogService auditLogService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createsAuditLog() throws Exception {
        CredentialAuditLogDto.AuditLogResponse response =
                CredentialAuditLogDto.AuditLogResponse.builder()
                        .id(1L)
                        .eventId("audit-event-id")
                        .projectId(1L)
                        .courseId(10L)
                        .action(CredentialAuditLog.Action.API_KEY_VIEWED)
                        .result(CredentialAuditLog.Result.DENIED)
                        .build();
        when(auditLogService.createAuditLog(any())).thenReturn(response);

        mockMvc.perform(post("/api/payments/internal/audit-logs")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("SCOPE_service.read")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 1,
                                  "courseId": 10,
                                  "userId": 20,
                                  "action": "API_KEY_VIEWED",
                                  "result": "DENIED",
                                  "detail": "프로젝트 접근 권한 없음"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value("audit-event-id"))
                .andExpect(jsonPath("$.data.result").value("DENIED"));
    }

    @Test
    void rejectsAuditLogWithoutRequiredResult() throws Exception {
        mockMvc.perform(post("/api/payments/internal/audit-logs")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("SCOPE_service.read")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 1,
                                  "courseId": 10,
                                  "action": "API_KEY_VIEWED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returnsDefaultThirtyDayDeniedCount() throws Exception {
        when(auditLogService.countDeniedAccesses(10L, 30))
                .thenReturn(CredentialAuditLogDto.DeniedAccessCountResponse.builder()
                        .credentialId(10L)
                        .periodDays(30)
                        .deniedAccessCount(3L)
                        .calculatedAt(LocalDateTime.of(2026, 8, 27, 5, 0))
                        .build());

        mockMvc.perform(get(
                        "/api/payments/internal/audit-logs/credentials/{credentialId}/denied-count",
                        10L
                ).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialId").value(10))
                .andExpect(jsonPath("$.data.periodDays").value(30))
                .andExpect(jsonPath("$.data.deniedAccessCount").value(3));

        verify(auditLogService).countDeniedAccesses(10L, 30);
    }

    @Test
    void rejectsDeniedCountPeriodOutsideSupportedRange() throws Exception {
        mockMvc.perform(get(
                        "/api/payments/internal/audit-logs/credentials/{credentialId}/denied-count",
                        10L
                ).queryParam("days", "0").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deniedCountRequiresAuthenticatedUser() throws Exception {
        mockMvc.perform(get(
                        "/api/payments/internal/audit-logs/credentials/{credentialId}/denied-count",
                        10L
                ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void auditLogCreationRequiresServiceScope() throws Exception {
        mockMvc.perform(post("/api/payments/internal/audit-logs")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 1,
                                  "courseId": 10,
                                  "action": "API_KEY_VIEWED",
                                  "result": "DENIED"
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
