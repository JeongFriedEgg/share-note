package com.example.share_note.integration;

import com.example.share_note.dto.user.*;
import com.example.share_note.domain.RefreshToken;
import com.example.share_note.domain.User;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.repository.ReactiveRefreshTokenRepository;
import com.example.share_note.repository.ReactiveUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebClient
public class UserIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveUserRepository reactiveUserRepository;

    @MockBean
    private ReactiveRefreshTokenRepository reactiveRefreshTokenRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("회원가입 성공 - 201 CREATED 응답")
    void register_success() {
        // given
        RegisterRequestDto request = RegisterRequestDto.builder()
                .username("testuser")
                .password("testpassword")
                .email("test@example.com")
                .build();

        String encodedPassword = "mockEncodedPassword";

        when(reactiveUserRepository.findByUsernameOrEmail(anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString()))
                .thenReturn(encodedPassword);
        when(reactiveUserRepository.save(any(User.class)))
                .thenReturn(Mono.just(User.builder()
                        .id(1L)
                        .username(request.getUsername())
                        .password(encodedPassword)
                        .email(request.getEmail())
                        .authorities("ROLE_USER")
                        .createdAt(LocalDateTime.now())
                        .build()));

        // when
        // then
        webTestClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isEqualTo("회원가입이 완료되었습니다.")
                .jsonPath("$.username").isEqualTo("testuser")
                .jsonPath("$.email").isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("회원가입 실패 - 중복된 사용자명 - 409 CONFLICT 응답")
    void register_failure_duplicationUsername() {
        // given
        RegisterRequestDto request = RegisterRequestDto.builder()
                .username("testuser")
                .password("testpassword")
                .email("test@example.com")
                .build();

        User existingUser = User.builder()
                .username("testuser")
                .email("otheremail@example.com")
                .build();

        when(reactiveUserRepository.findByUsernameOrEmail(anyString(), anyString()))
                .thenReturn(Mono.just(existingUser));

        // when
        // then
        webTestClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.DUPLICATE_USERNAME.getMessage());
    }

    @Test
    @DisplayName("회원가입 실패 - 중복된 이메일 - 409 CONFLICT 응답")
    void register_failure_duplicationEmail() {
        // given
        RegisterRequestDto request = RegisterRequestDto.builder()
                .username("testuser")
                .password("testpassword")
                .email("test@example.com")
                .build();

        User existingUserWithEmail = User.builder()
                .username("otheruser")
                .email("test@example.com")
                .build();
        when(reactiveUserRepository.findByUsernameOrEmail(anyString(), anyString()))
                .thenReturn(Mono.just(existingUserWithEmail));

        // when
        // then
        webTestClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.DUPLICATE_EMAIL.getMessage());
    }

    @Test
    @DisplayName("로그인 성공 - 200 OK 응답 및 토큰 반환")
    void login_success() {
        // given
        LoginRequestDto requestDto = LoginRequestDto.builder()
                .username("testuser")
                .password("testpassword")
                .build();

        User user = User.builder()
                .id(1L)
                .username("testuser")
                .password("encodedPassword")
                .authorities("ROLE_USER")
                .build();

        when(reactiveUserRepository.findByUsername(anyString()))
                .thenReturn(Mono.just(user));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(reactiveRefreshTokenRepository.save(any()))
                .thenReturn(Mono.just(new RefreshToken()));

        // when
        // then
        webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestDto)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.refreshToken").isNotEmpty();
    }

    @Test
    @DisplayName("로그인 실패 - 사용자 없음 - 401 UNAUTHORIZED 응답")
    void login_failure_userNotFound() {
        // given
        LoginRequestDto requestDto = LoginRequestDto.builder()
                .username("nonexistentuser")
                .password("password123")
                .build();

        when(reactiveUserRepository.findByUsername(anyString()))
                .thenReturn(Mono.empty());

        // when
        // then
        webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestDto)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치 - 401 UNAUTHORIZED 응답")
    void login_failure_invalidPassword() {
        // given
        LoginRequestDto requestDto = LoginRequestDto.builder()
                .username("testuser")
                .password("wrongpassword")
                .build();

        User user = User.builder()
                .username("testuser")
                .password("encodedPassword")
                .build();

        when(reactiveUserRepository.findByUsername(anyString()))
                .thenReturn(Mono.just(user));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(false);

        // when
        // then
        webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestDto)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ErrorCode.INVALID_PASSWORD.getMessage());
    }

    @Test
    @DisplayName("로그아웃 성공 - 200 OK 응답")
    void logout_success() {
        // given
        LogoutRequestDto request = LogoutRequestDto.builder()
                .refreshToken("validRefreshToken")
                .build();

        when(reactiveRefreshTokenRepository.deleteByRefreshToken(anyString()))
                .thenReturn(Mono.empty());

        // when
        // then
        webTestClient.post().uri("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody().isEmpty();
    }
}
