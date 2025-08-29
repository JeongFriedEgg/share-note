package com.example.share_note.security;

import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.JwtAuthenticationException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;


@Slf4j
@Component
public class JwtTokenProvider {


    private final SecretKey secretKey;
    private final Duration expirationDuration;


    public JwtTokenProvider(@Value("${jwt.secret}") String key,
                            @Value("${jwt.expiration}") Long expirationDuration) {
        byte[] bytes = Decoders.BASE64.decode(key);
        this.secretKey = Keys.hmacShaKeyFor(bytes);
        this.expirationDuration = Duration.ofHours(expirationDuration);
    }

    /**
     * Authentication 객체로부터 JWT Access Token 생성
     *
     * @param authentication 현재 인증된 사용자의 정보
     * @return 생성된 토큰 정보(TokenInfoDto
     */
    public String createToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        String email = userDetails.getEmail();

        Instant now = Instant.now();
        Instant expirationInstant = now.plus(expirationDuration);
        Date expirationDate = Date.from(expirationInstant);

        return Jwts.builder()
                .subject(authentication.getName())
                .claim("username", username)
                .claim("email", email)
                .claim("authorities", authorities)
                .issuedAt(Date.from(now))
                .expiration(expirationDate)
                .signWith(secretKey)
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
            throw new JwtAuthenticationException(ErrorCode.EXPIRED_TOKEN);
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            throw new JwtAuthenticationException(ErrorCode.INVALID_SIGNATURE);
        } catch (MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            throw new JwtAuthenticationException(ErrorCode.INVALID_TOKEN);
        }
    }
}
