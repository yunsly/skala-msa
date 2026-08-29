package com.lecture.payment.controller;

import com.lecture.payment.config.SecurityConfig;
import com.lecture.payment.dto.PaymentDto;
import com.lecture.payment.entity.Payment;
import com.lecture.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void returnsActiveGrantsForAuthenticatedLeader() throws Exception {
        when(paymentService.getActiveGrants(2L, "Test token"))
                .thenReturn(List.of(PaymentDto.ActiveGrantResponse.builder()
                        .id(10L)
                        .projectId(3L)
                        .projectName("Payment")
                        .userId(7L)
                        .build()));

        mockMvc.perform(get("/api/payments/active")
                        .with(jwt())
                        .header("Authorization", "Test token")
                        .header("X-User-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].projectName").value("Payment"));
    }

    @Test
    void revokesCompletedGrant() throws Exception {
        when(paymentService.revoke(10L, 2L, "프로젝트 종료", "Test token"))
                .thenReturn(PaymentDto.PaymentResponse.builder()
                        .paymentId(10L)
                        .status(Payment.Status.CANCELLED)
                        .build());

        mockMvc.perform(post("/api/payments/{id}/revoke", 10L)
                        .with(jwt())
                        .header("Authorization", "Test token")
                        .header("X-User-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisionReason":"프로젝트 종료"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(10))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(paymentService).revoke(10L, 2L, "프로젝트 종료", "Test token");
    }

    @Test
    void returnsForbiddenWhenActorCannotRevokeGrant() throws Exception {
        when(paymentService.revoke(10L, 7L, "회수", "Test token"))
                .thenThrow(new AccessDeniedException("회수 권한이 없습니다."));

        mockMvc.perform(post("/api/payments/{id}/revoke", 10L)
                        .with(jwt())
                        .header("Authorization", "Test token")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisionReason":"회수"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
