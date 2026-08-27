package com.lecture.user.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedUserResolverTest {

    private final AuthenticatedUserResolver resolver = new AuthenticatedUserResolver();

    @Test
    void resolvesUserFromJwtAndMatchingGatewayHeaders() {
        Jwt jwt = jwt("7", Map.of("user_id", 7L, "role", "STUDENT"));

        AuthenticatedUser authenticatedUser = resolver.resolve(jwt, 7L, "ROLE_STUDENT");

        assertThat(authenticatedUser.userId()).isEqualTo(7L);
    }

    @Test
    void fallsBackToSubjectWhenUserIdClaimIsMissing() {
        Jwt jwt = jwt("9", Map.of("role", "INSTRUCTOR"));

        AuthenticatedUser authenticatedUser = resolver.resolve(jwt, null, null);

        assertThat(authenticatedUser.userId()).isEqualTo(9L);
    }

    @Test
    void rejectsMismatchedGatewayUserId() {
        Jwt jwt = jwt("7", Map.of("user_id", 7L, "role", "STUDENT"));

        assertThatThrownBy(() -> resolver.resolve(jwt, 8L, "STUDENT"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("사용자 ID");
    }

    @Test
    void rejectsMismatchedGatewayRole() {
        Jwt jwt = jwt("7", Map.of("user_id", 7L, "role", "STUDENT"));

        assertThatThrownBy(() -> resolver.resolve(jwt, 7L, "INSTRUCTOR"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("역할");
    }

    private Jwt jwt(String subject, Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
