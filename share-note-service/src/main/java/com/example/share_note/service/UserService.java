package com.example.share_note.service;

import com.example.share_note.dto.user.LoginRequestDto;
import com.example.share_note.dto.user.LoginResponseDto;
import com.example.share_note.dto.user.RegisterRequestDto;
import com.example.share_note.dto.user.RegisterResponseDto;
import reactor.core.publisher.Mono;

public interface UserService {
    Mono<RegisterResponseDto> register(RegisterRequestDto request);

    Mono<LoginResponseDto> loginAndGenerateToken(LoginRequestDto request);

    Mono<Void> logout(String refreshToken);
}
