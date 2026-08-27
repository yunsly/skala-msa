package com.lecture.enrollment.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Component
public class ServiceTokenProvider {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private volatile String accessToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(
            WebClient.Builder webClientBuilder,
            @Value("${service.auth-server.url}") String authServerUrl,
            @Value("${service.oauth.client-id}") String clientId,
            @Value("${service.oauth.client-secret}") String clientSecret
    ) {
        this.webClient = webClientBuilder.clone().baseUrl(authServerUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public synchronized String getAccessToken() {
        if (accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return accessToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "service.read");

        TokenResponse response = webClient.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("서비스 Access Token을 발급받지 못했습니다.");
        }
        accessToken = response.accessToken();
        expiresAt = Instant.now().plusSeconds(response.expiresIn());
        return accessToken;
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {
    }
}
