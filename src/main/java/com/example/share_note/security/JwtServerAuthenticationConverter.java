package com.example.share_note.security;

import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.JwtAuthenticationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {

    private final JwtTokenProvider jwtTokenProvider;
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * ServerWebExchange 에서 Authorization 헤더를 파싱하여 JWT 를 추출
     * 추출된 JWT 의 유효성 검사(서명이 올바른지, 만료되지 않았는지) -> validateToken 이 이를 처리함.
     *
     * 유효한 경우 사용자 정보를 기반으로 Mono<Authentication>을 생성
     *
     * @param exchange
     * @return
     */
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(authValue -> authValue.startsWith(BEARER_PREFIX))
                .flatMap(authValue -> {
                    try {
                        String token = authValue.substring(BEARER_PREFIX.length());

                        jwtTokenProvider.validateToken(token);

                        Authentication authentication = jwtTokenProvider.getAuthentication(token);

                        return Mono.just(authentication);
                    } catch (JwtAuthenticationException e) {
                        return Mono.error(e);
                    } catch (Exception e) {
                        return Mono.error(new JwtAuthenticationException(ErrorCode.UNEXPECTED_ERROR));
                    }
                });
    }
}
