package com.example.share_note.service;

import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.user.LoginRequestDto;
import com.example.share_note.dto.user.LoginResponseDto;
import com.example.share_note.dto.user.RegisterRequestDto;
import com.example.share_note.dto.user.RegisterResponseDto;
import com.example.share_note.domain.RefreshToken;
import com.example.share_note.domain.User;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.user.UserLoginException;
import com.example.share_note.exception.user.UserRegistrationException;
import com.example.share_note.repository.ReactiveRefreshTokenRepository;
import com.example.share_note.repository.ReactiveUserRepository;
import com.example.share_note.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final ReactiveUserRepository reactiveUserRepository;
    private final ReactiveRefreshTokenRepository reactiveRefreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public Mono<RegisterResponseDto> register(RegisterRequestDto request) {
        return reactiveUserRepository.findByUsernameOrEmail(request.getUsername(),request.getEmail())
                .flatMap(existingUser -> {
                    if (existingUser.getUsername().equals(request.getUsername())) {
                        return Mono.error(new UserRegistrationException(ErrorCode.DUPLICATE_USERNAME));
                    }
                    if (existingUser.getEmail().equals(request.getEmail())) {
                        return Mono.error(new UserRegistrationException(ErrorCode.DUPLICATE_EMAIL));
                    }
                    return null;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    User user = User.builder()
                            .username(request.getUsername())
                            .password(passwordEncoder.encode(request.getPassword()))
                            .email(request.getEmail())
                            .authorities("ROLE_USER")
                            .createdAt(LocalDateTime.now())
                            .build();

                    return reactiveUserRepository.save(user)
                            .map(savedUser -> RegisterResponseDto.builder()
                                    .message("회원가입이 완료되었습니다.")
                                    .username(savedUser.getUsername())
                                    .email(savedUser.getEmail())
                                    .build());
                }))
                .cast(RegisterResponseDto.class);
    }

    public Mono<LoginResponseDto> loginAndGenerateToken(LoginRequestDto request) {
        return Mono.deferContextual(ctx -> {
            String clientIp = ctx.get("clientIp");
            String deviceName = ctx.get("deviceName");
            String osName = ctx.get("osName");
            String browserName = ctx.get("browserName");

            return reactiveUserRepository.findByUsername(request.getUsername())
                    .switchIfEmpty(Mono.error(new UserLoginException(ErrorCode.USER_NOT_FOUND)))
                    .flatMap(user -> {
                        if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                            List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(user.getAuthorities()));
                            Authentication authentication = new UsernamePasswordAuthenticationToken(
                                    new CustomUserDetails(user.getId(), user.getUsername(), user.getPassword(), user.getAuthorities(), user.getEmail()),
                                    null,
                                    authorities
                            );

                            String accessToken = jwtTokenProvider.createAccessToken(authentication);
                            String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

                            RefreshToken refreshTokenEntity = RefreshToken.builder()
                                    .refreshToken(refreshToken)
                                    .username(user.getUsername())
                                    .expirationDate(LocalDateTime.now().plusHours(720))
                                    .ipAddress(clientIp)
                                    .deviceName(deviceName)
                                    .osName(osName)
                                    .browserName(browserName)
                                    .build();

                            return reactiveRefreshTokenRepository.save(refreshTokenEntity)
                                    .thenReturn(LoginResponseDto.builder()
                                            .accessToken(accessToken)
                                            .refreshToken(refreshToken)
                                            .build());
                        } else {
                            return Mono.error(new UserLoginException(ErrorCode.INVALID_PASSWORD));
                        }
                    });
        });
    }

    public Mono<Void> logout(String refreshToken) {
        return reactiveRefreshTokenRepository.deleteByRefreshToken(refreshToken);
    }
}
