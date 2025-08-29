package com.example.share_note.service;

import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.dto.user.LoginRequestDto;
import com.example.share_note.dto.user.LoginResponseDto;
import com.example.share_note.dto.user.RegisterRequestDto;
import com.example.share_note.dto.user.RegisterResponseDto;
import com.example.share_note.entity.UserEntity;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.user.UserLoginException;
import com.example.share_note.exception.user.UserRegistrationException;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public Mono<RegisterResponseDto> register(RegisterRequestDto requestDto) {
        return reactiveUserRepository.findByUsernameOrEmail(requestDto.getUsername(),requestDto.getEmail())
                .flatMap(existingUser -> {
                    if (existingUser.getUsername().equals(requestDto.getUsername())) {
                        return Mono.error(new UserRegistrationException(ErrorCode.DUPLICATE_USERNAME));
                    }
                    if (existingUser.getEmail().equals(requestDto.getEmail())) {
                        return Mono.error(new UserRegistrationException(ErrorCode.DUPLICATE_EMAIL));
                    }
                    return null;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    UserEntity user = UserEntity.builder()
                            .username(requestDto.getUsername())
                            .password(passwordEncoder.encode(requestDto.getPassword()))
                            .email(requestDto.getEmail())
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

    public Mono<LoginResponseDto> loginAndGenerateToken(LoginRequestDto requestDto) {
        return reactiveUserRepository.findByUsername(requestDto.getUsername())
                .switchIfEmpty(Mono.error(new UserLoginException(ErrorCode.USER_NOT_FOUND)))
                .flatMap(user -> {
                    if (passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
                        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(user.getAuthorities()));
                        Authentication authentication = new UsernamePasswordAuthenticationToken(
                                new CustomUserDetails(user.getUsername(),user.getPassword(),user.getAuthorities(),user.getEmail()),
                                null,
                                authorities
                        );

                        String accessToken = jwtTokenProvider.createToken(authentication);

                        return Mono.just(LoginResponseDto.builder()
                                .accessToken(accessToken)
                                .build());
                    }else {
                        return Mono.error(new UserLoginException(ErrorCode.INVALID_PASSWORD));
                    }
                });
    }
}
