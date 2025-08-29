package com.example.share_note.controller;

import com.example.share_note.dto.user.LoginRequestDto;
import com.example.share_note.dto.user.LoginResponseDto;
import com.example.share_note.dto.user.RegisterRequestDto;
import com.example.share_note.dto.user.RegisterResponseDto;
import com.example.share_note.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterResponseDto>> register(@RequestBody RegisterRequestDto requestDto) {
        return userService.register(requestDto)
                .map(responseDto -> new ResponseEntity<>(responseDto, HttpStatus.CREATED));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponseDto>> login(@RequestBody LoginRequestDto requestDto) {
        return userService.loginAndGenerateToken(requestDto)
                .map(responseDto -> new ResponseEntity<>(responseDto, HttpStatus.OK));
    }
}
