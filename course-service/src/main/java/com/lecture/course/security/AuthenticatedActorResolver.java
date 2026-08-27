package com.lecture.course.security;

import com.lecture.course.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticatedActorResolver {

    private final UserServiceClient userServiceClient;

    public AuthenticatedActor resolve(Jwt jwt, Long gatewayUserId) {
        Long tokenUserId = parseUserId(jwt);
        if (gatewayUserId != null && !gatewayUserId.equals(tokenUserId)) {
            throw new AccessDeniedException("Gateway 사용자 ID와 JWT 사용자 ID가 일치하지 않습니다.");
        }
        UserServiceClient.UserInfo user = userServiceClient.getUser(tokenUserId);
        return new AuthenticatedActor(user.id(), user.role());
    }

    private Long parseUserId(Jwt jwt) {
        Object claim = jwt.getClaims().get("user_id");
        String raw = claim == null ? jwt.getSubject() : String.valueOf(claim);
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException | NullPointerException exception) {
            throw new AccessDeniedException("JWT에 올바른 사용자 ID가 없습니다.");
        }
    }
}
