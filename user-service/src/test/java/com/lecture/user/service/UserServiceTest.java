package com.lecture.user.service;

import com.lecture.user.dto.UserDto;
import com.lecture.user.entity.User;
import com.lecture.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void memberRegistrationStoresBcryptPassword() {
        UserDto.RegisterRequest request = registerRequest(User.Role.MEMBER);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(User.Role.MEMBER);
        assertThat(savedUser.getPassword()).isNotEqualTo(request.getPassword());
        assertThat(passwordEncoder.matches(request.getPassword(), savedUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", savedUser.getPassword())).isFalse();
    }

    @Test
    void leaderCanRegister() {
        UserDto.RegisterRequest request = registerRequest(User.Role.LEADER);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto.UserResponse response = userService.register(request);

        assertThat(response.getRole()).isEqualTo(User.Role.LEADER);
    }

    @Test
    void adminCannotUsePublicRegistration() {
        UserDto.RegisterRequest request = registerRequest(User.Role.ADMIN);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MEMBER 또는 LEADER");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void duplicateEmailIsRejected() {
        UserDto.RegisterRequest request = registerRequest(User.Role.MEMBER);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 이메일");
    }

    @Test
    void currentUserRoleComesFromDomainDatabase() {
        User leader = user(1L, User.Role.LEADER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(leader));

        UserDto.UserResponse response = userService.getCurrentUser(1L);

        assertThat(response.getRole()).isEqualTo(User.Role.LEADER);
    }

    @Test
    void memberCannotReadAnotherUserById() {
        User member = user(1L, User.Role.MEMBER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> userService.getUserByIdAsAdmin(1L, 2L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ADMIN");
        verify(userRepository, never()).findById(2L);
    }

    @Test
    void adminCanReadAnotherUserById() {
        User admin = user(1L, User.Role.ADMIN);
        User member = user(2L, User.Role.MEMBER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        UserDto.UserResponse response = userService.getUserByIdAsAdmin(1L, 2L);

        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getRole()).isEqualTo(User.Role.MEMBER);
    }

    @Test
    void onlyThreeDomainRolesExist() {
        assertThat(User.Role.values()).containsExactly(
                User.Role.ADMIN,
                User.Role.LEADER,
                User.Role.MEMBER
        );
    }

    private UserDto.RegisterRequest registerRequest(User.Role role) {
        return UserDto.RegisterRequest.builder()
                .email("member@example.com")
                .password("password123")
                .name("사용자")
                .role(role)
                .build();
    }

    private User user(Long id, User.Role role) {
        return User.builder()
                .id(id)
                .email("user" + id + "@example.com")
                .password("bcrypt")
                .name("사용자")
                .role(role)
                .build();
    }
}
