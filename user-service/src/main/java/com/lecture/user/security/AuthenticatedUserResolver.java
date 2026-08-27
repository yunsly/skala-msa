package com.lecture.user.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserResolver {

    public AuthenticatedUser resolve(
            Jwt jwt,
            Long gatewayUserId,
            String gatewayRole
    ) {
        // user_id is signed by the auth-server and identifies the domain user.
        // The legacy role claim is checked only for Gateway tampering; authorization
        // must use the current ADMIN/LEADER/MEMBER value stored in user-service DB.
        Long tokenUserId = parseUserId(jwt);

        if (gatewayUserId != null && !gatewayUserId.equals(tokenUserId)) {
            throw new AccessDeniedException("Gateway 사용자 ID와 JWT 사용자 ID가 일치하지 않습니다.");
        }
        if (gatewayRole != null && !gatewayRole.isBlank()) {
            String tokenRole = normalizeRole(jwt.getClaimAsString("role"));
            String headerRole = normalizeRole(gatewayRole);
            if (!headerRole.equals(tokenRole)) {
                throw new AccessDeniedException("Gateway 역할과 JWT 역할이 일치하지 않습니다.");
            }
        }
        return new AuthenticatedUser(tokenUserId);
    }

    private Long parseUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaims().get("user_id");
        String rawUserId = userIdClaim == null
                ? jwt.getSubject()
                : String.valueOf(userIdClaim);
        try {
            return Long.valueOf(rawUserId);
        } catch (NumberFormatException | NullPointerException exception) {
            throw new AccessDeniedException("JWT에 올바른 사용자 ID가 없습니다.");
        }
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new AccessDeniedException("JWT 또는 Gateway 헤더에 역할이 없습니다.");
        }
        return rawRole.startsWith("ROLE_")
                ? rawRole.substring("ROLE_".length())
                : rawRole;
    }
}
