package com.lecture.enrollment.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceClientTest {

    @Test
    void sendsServiceTokenWhenRequestingApproval() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> {
                    capturedRequest.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(
                                    HttpHeaders.CONTENT_TYPE,
                                    MediaType.APPLICATION_JSON_VALUE
                            )
                            .body("""
                                    {
                                      "paymentId": 100,
                                      "status": "PENDING"
                                    }
                                    """)
                            .build());
                });
        ServiceTokenProvider tokenProvider = mock(ServiceTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("service-access-token");

        PaymentServiceClient client = new PaymentServiceClient(
                webClientBuilder,
                tokenProvider
        );

        PaymentServiceClient.PaymentResult result = client.requestApproval(
                10L,
                20L,
                30L
        );

        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer service-access-token");
        assertThat(result).isNotNull();
        assertThat(result.getPaymentId()).isEqualTo(100L);
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }
}
