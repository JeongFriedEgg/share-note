package com.example.share_note.security;

import com.example.share_note.exception.AuthenticationFailedException;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.service.CustomReactiveUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final CustomReactiveUserDetailsService customReactiveUserDetailsService;

    /**
     * ServerAuthenticationConverter 의 구현체인 JwtServerAuthenticationConverter 에서 생성된
     * Authentication 객체를 받아 인증을 수행
     *
     * 이미 JwtServerAuthenticationConverter에서 토큰을 통해 사용자 정보를 가져왔으므로
     * 별도의 추가 검증이 필요하지 않는다.
     * 여기서는 토큰에서 가져온 정보가 유효한지 최종적으로 확인만 한다.
     * 실직적인 인증 로직은 이미 컨버터에서 처리하였다.
     * 예를 들어, username이 비어있는지 확인하는 등의 로직을 추가할 수 있다.
     *
     * @param authentication
     * @return
     */
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (authentication.getPrincipal() == null || authentication.getPrincipal().toString().isEmpty()) {
            return Mono.error(new AuthenticationFailedException(ErrorCode.AUTHENTICATION_FAILED));
        }

        return Mono.just(authentication);
    }
}
