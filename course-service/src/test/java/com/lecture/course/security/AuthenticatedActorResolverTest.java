package com.lecture.course.security;

import com.lecture.course.client.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedActorResolverTest {

    private final UserServiceClient userServiceClient = mock(UserServiceClient.class);
    private final AuthenticatedActorResolver resolver = new AuthenticatedActorResolver(userServiceClient);

    @Test
    void resolvesDomainRoleFromUserServiceInsteadOfLegacyJwtRole() {
        Jwt jwt = jwt("7", Map.of("user_id", 7L, "role", "INSTRUCTOR"));
        when(userServiceClient.getUser(7L)).thenReturn(new UserServiceClient.UserInfo(
                7L,
                "leader@example.com",
                "리더",
                AuthenticatedActor.Role.LEADER
        ));

        AuthenticatedActor actor = resolver.resolve(jwt, 7L);

        assertThat(actor.userId()).isEqualTo(7L);
        assertThat(actor.role()).isEqualTo(AuthenticatedActor.Role.LEADER);
    }

    @Test
    void rejectsMismatchedGatewayUserId() {
        Jwt jwt = jwt("7", Map.of("user_id", 7L));

        assertThatThrownBy(() -> resolver.resolve(jwt, 8L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("사용자 ID");
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
