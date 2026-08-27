package com.lecture.course.client;

import com.lecture.course.security.AuthenticatedActor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class UserServiceClient {

    private final WebClient webClient;
    private final ServiceTokenProvider tokenProvider;

    public UserServiceClient(
            WebClient.Builder webClientBuilder,
            ServiceTokenProvider tokenProvider,
            @Value("${service.user-service.url}") String userServiceUrl
    ) {
        this.webClient = webClientBuilder.clone().baseUrl(userServiceUrl).build();
        this.tokenProvider = tokenProvider;
    }

    public UserInfo getUser(Long userId) {
        UserInfo response = webClient.get()
                .uri("/api/users/internal/{id}", userId)
                .headers(headers -> headers.setBearerAuth(tokenProvider.getAccessToken()))
                .retrieve()
                .bodyToMono(UserInfo.class)
                .block();
        if (response == null || response.role() == null) {
            throw new IllegalStateException("User Service에서 사용자 권한을 조회하지 못했습니다.");
        }
        return response;
    }

    public record UserInfo(Long id, String email, String name, AuthenticatedActor.Role role) {
    }
}
