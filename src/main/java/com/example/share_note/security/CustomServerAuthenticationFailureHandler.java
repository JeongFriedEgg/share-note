package com.example.share_note.security;

import com.example.share_note.exception.AuthenticationFailedException;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.ErrorResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomServerAuthenticationFailureHandler implements ServerAuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        log.error("Authentication failed: {}", exception.getMessage());

        ServerWebExchange exchange = webFilterExchange.getExchange();

        ErrorCode errorCode;

        if (exception instanceof AuthenticationFailedException) {
            errorCode = ((AuthenticationFailedException) exception).getErrorCode();
        } else if (exception instanceof BadCredentialsException) {
            errorCode = ErrorCode.AUTHENTICATION_FAILED;
        } else {
            errorCode = ErrorCode.UNEXPECTED_ERROR;
        }

        return buildErrorResponse(exchange, errorCode, HttpStatus.UNAUTHORIZED);
    }

    private Mono<Void> buildErrorResponse(ServerWebExchange exchange, ErrorCode errorCode, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .message(errorCode.getMessage())
                .status(status)
                .code(errorCode.getCode())
                .timestamp(LocalDateTime.now())
                .build();

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}

