package com.example.share_note.controller;

import com.example.share_note.dto.user.*;
import com.example.share_note.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterResponseDto>> register(@RequestBody RegisterRequestDto request) {
        return userService.register(request)
                .map(responseDto -> new ResponseEntity<>(responseDto, HttpStatus.CREATED));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponseDto>> login(@RequestBody LoginRequestDto request) {
        return userService.loginAndGenerateToken(request)
                .map(responseDto -> new ResponseEntity<>(responseDto, HttpStatus.OK));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@Valid @RequestBody LogoutRequestDto request) {
        return userService.logout(request.getRefreshToken())
                .thenReturn(ResponseEntity.ok().build());
    }
}
