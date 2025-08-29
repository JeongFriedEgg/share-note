package com.example.share_note.security;

import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.ErrorResponseDto;
import com.example.share_note.exception.JwtAuthenticationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
@Order(-1)
@RequiredArgsConstructor
public class CustomErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof JwtAuthenticationException) {
            JwtAuthenticationException jwtEx = (JwtAuthenticationException) ex;
            ErrorCode errorCode = jwtEx.getErrorCode();
            HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;

            ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                    .message(errorCode.getMessage())
                    .status(httpStatus)
                    .code(errorCode.getCode())
                    .timestamp(LocalDateTime.now())
                    .build();

            byte[] bytes;
            try {
                bytes = objectMapper.writeValueAsBytes(errorResponse);
            } catch (JsonProcessingException e) {
                return Mono.error(e);
            }

            exchange.getResponse().setStatusCode(httpStatus);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        }

        return Mono.error(ex);
    }
}
