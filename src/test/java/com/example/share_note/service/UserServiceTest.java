package com.example.share_note.service;

import com.example.share_note.dto.user.LoginRequestDto;
import com.example.share_note.dto.user.LoginResponseDto;
import com.example.share_note.dto.user.RegisterRequestDto;
import com.example.share_note.dto.user.RegisterResponseDto;
import com.example.share_note.domain.RefreshToken;
import com.example.share_note.domain.User;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.UserException;
import com.example.share_note.repository.ReactiveRefreshTokenRepository;
import com.example.share_note.repository.ReactiveUserRepository;
import com.example.share_note.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private ReactiveUserRepository reactiveUserRepository;

    @Mock
    private ReactiveRefreshTokenRepository reactiveRefreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserService userService;

    private User user;

    @Captor
    ArgumentCaptor<RefreshToken> refreshTokenCaptor;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .password("testpassword")
                .email("test@example.com")
                .authorities("ROLE_USER")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("회원가입 성공 - 사용자 저장 후 응답 DTO 반환")
    void register_success() {
        // given
        RegisterRequestDto requestDto = RegisterRequestDto.builder()
                .username("newuser")
                .password("password123")
                .email("newuser@example.com")
                .build();

        when(reactiveUserRepository.findByUsernameOrEmail(anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString()))
                .thenReturn("testpassword");
        when(reactiveUserRepository.save(any(User.class)))
                .thenReturn(Mono.just(user));

        // when
        Mono<RegisterResponseDto> responseDtoMono = userService.register(requestDto);

        // then
        StepVerifier.create(responseDtoMono)
                .expectNextMatches(response ->
                        "회원가입이 완료되었습니다.".equals(response.getMessage()) &&
                                "testuser".equals(response.getUsername()) &&
                                "test@example.com".equals(response.getEmail()))
                .verifyComplete();
    }

    @Test
    @DisplayName("회원가입 실패 - 중복된 아이디")
    void register_failure_duplicateUsername() {
        // given
        RegisterRequestDto requestDto = RegisterRequestDto.builder()
                .username("testuser")
                .password("password123")
                .email("newuser@example.com")
                .build();

        when(reactiveUserRepository.findByUsernameOrEmail(anyString(), anyString()))
                .thenReturn(Mono.just(user));

        // when
        Mono<RegisterResponseDto> responseDtoMono = userService.register(requestDto);

        // then
        StepVerifier.create(responseDtoMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof UserException &&
                                ((UserException) throwable).getErrorCode() == ErrorCode.DUPLICATE_USERNAME)
                .verify();
    }

    @Test
    @DisplayName("회원가입 실패 - 중복된 이메일")
    void register_failure_duplicationEmail() {
        // given
        RegisterRequestDto requestDto = RegisterRequestDto.builder()
                .username("newuser")
                .password("password123")
                .email("test@example.com")
                .build();

        when(reactiveUserRepository.findByUsernameOrEmail(anyString(), anyString()))
                .thenReturn(Mono.just(user));

        // when
        Mono<RegisterResponseDto> responseDtoMono = userService.register(requestDto);

        // then
        StepVerifier.create(responseDtoMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof UserException &&
                                ((UserException) throwable).getErrorCode() == ErrorCode.DUPLICATE_EMAIL)
                .verify();
    }

    @Test
    @DisplayName("로그인 성공 - 토큰 생성 및 리프레시 토큰 저장")
    void login_success() {
        // given
        LoginRequestDto requestDto = LoginRequestDto.builder()
                .username("testuser")
                .password("testpassword")
                .build();

        String clientIp = "127.0.0.1";
        String deviceName = "Test-Device";
        String osName = "macOS";
        String browserName = "Chrome";

        when(reactiveUserRepository.findByUsername(anyString()))
                .thenReturn(Mono.just(user));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(jwtTokenProvider.createAccessToken(any()))
                .thenReturn("mockAccessToken");
        when(jwtTokenProvider.createRefreshToken(any()))
                .thenReturn("mockRefreshToken");
        when(reactiveRefreshTokenRepository.save(any(RefreshToken.class)))
                .thenReturn(Mono.just(new RefreshToken()));

        // when
        Mono<LoginResponseDto> responseDtoMono = userService.loginAndGenerateToken(requestDto)
                .contextWrite(Context.of("clientIp", clientIp,
                        "deviceName", deviceName,
                        "osName", osName,
                        "browserName", browserName));

        // then
        StepVerifier.create(responseDtoMono)
                .expectNextMatches(response ->
                        "mockAccessToken".equals(response.getAccessToken()) &&
                        "mockRefreshToken".equals(response.getRefreshToken()))
                .verifyComplete();

        verify(reactiveRefreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken capturedToken = refreshTokenCaptor.getValue();

        assertEquals(clientIp, capturedToken.getIpAddress());
        assertEquals(deviceName, capturedToken.getDeviceName());
        assertEquals(osName, capturedToken.getOsName());
        assertEquals(browserName, capturedToken.getBrowserName());
        assertEquals("mockRefreshToken", capturedToken.getRefreshToken());
    }

    @Test
    @DisplayName("로그인 실패 - 사용자를 찾을 수 없음")
    void login_failure_userNotFound() {
        // given
        LoginRequestDto requestDto = LoginRequestDto.builder()
                .username("nonexistentuser")
                .password("password123")
                .build();

        when(reactiveUserRepository.findByUsername(anyString()))
                .thenReturn(Mono.empty());

        // when
        Mono<LoginResponseDto> responseDtoMono = userService.loginAndGenerateToken(requestDto)
                .contextWrite(Context.of("clientIp", "127.0.0.1",
                        "deviceName", "Test-Device",
                        "osName", "macOS",
                        "browserName", "Chrome"));

        // then
        StepVerifier.create(responseDtoMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof UserException &&
                                ((UserException) throwable).getErrorCode() == ErrorCode.USER_NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void login_failure_invalidPassword() {
        // given
        LoginRequestDto requestDto = LoginRequestDto.builder()
                .username("testuser")
                .password("wrongpassword")
                .build();

        when(reactiveUserRepository.findByUsername(anyString()))
                .thenReturn(Mono.just(user));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(false);

        // when
        Mono<LoginResponseDto> responseDtoMono = userService.loginAndGenerateToken(requestDto)
                .contextWrite(Context.of("clientIp", "127.0.0.1",
                        "deviceName", "Test-Device",
                        "osName", "macOS",
                        "browserName", "Chrome"));

        // then
        StepVerifier.create(responseDtoMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof  UserException &&
                                ((UserException) throwable).getErrorCode() == ErrorCode.INVALID_PASSWORD)
                .verify();
    }

    @Test
    @DisplayName("로그아웃 성공 - 리프레시 토큰 삭제")
    void logout_success() {
        // given
        String refreshToken = "validRefreshToken";
        when(reactiveRefreshTokenRepository.deleteByRefreshToken(refreshToken))
                .thenReturn(Mono.empty());

        // when
        Mono<Void> responseDtoMono = userService.logout(refreshToken);

        // then
        StepVerifier.create(responseDtoMono)
                .verifyComplete();
    }
}
