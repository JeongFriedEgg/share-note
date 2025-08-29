package com.example.share_note.security;

import com.example.share_note.exception.AuthenticationFailedException;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.service.CustomReactiveUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomReactiveUserDetailsService customReactiveUserDetailsService;

    /**
     * ServerAuthenticationConverter 의 구현체인 JwtServerAuthenticationConverter 에서 생성된
     * Authentication 객체를 받아 인증을 수행
     *
     * @param authentication
     * @return
     */
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof String)) {
            return Mono.error(new AuthenticationFailedException(ErrorCode.AUTHENTICATION_FAILED));
        }

        String username = (String) authentication.getPrincipal();

        return customReactiveUserDetailsService.findByUsername(username)
                .doOnError(e -> log.error("Error occurred while finding user: {}", e.getMessage()))
                .onErrorMap(throwable -> new AuthenticationFailedException(ErrorCode.UNEXPECTED_ERROR))
                .switchIfEmpty(Mono.defer(() ->
                        Mono.error(new AuthenticationFailedException(ErrorCode.AUTHENTICATION_FAILED))
                ))
                .map(userDetails ->
                     new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    )
                );
    }
}
