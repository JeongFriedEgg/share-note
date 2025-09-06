package com.example.share_note.security;

import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.exception.ErrorCode;
import com.example.share_note.exception.JwtAuthenticationException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;


@Slf4j
@Component
public class JwtTokenProvider {


    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;


    public JwtTokenProvider(@Value("${jwt.secret}") String key,
                            @Value("${jwt.access-token.expiration}") long accessTokenExpiration,
                            @Value("${jwt.refresh-token.expiration}") long refreshTokenExpiration) {
        byte[] bytes = Decoders.BASE64.decode(key);
        this.secretKey = Keys.hmacShaKeyFor(bytes);
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String createAccessToken(Authentication authentication) {
        return createToken(authentication, accessTokenExpiration);
    }

    public String createRefreshToken(Authentication authentication) {
        return createToken(authentication, refreshTokenExpiration);
    }

    /**
     * Authentication 객체로부터 JWT Access Token 생성
     *
     * @param authentication 현재 인증된 사용자의 정보
     * @return 생성된 토큰 정보(TokenInfoDto
     */
    private String createToken(Authentication authentication, long expirationHours) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getId();
        String username = userDetails.getUsername();
        String email = userDetails.getEmail();

        Instant now = Instant.now();
        Instant expirationInstant = now.plus(Duration.ofMinutes(expirationHours));
        Date expirationDate = Date.from(expirationInstant);

        return Jwts.builder()
                .subject(authentication.getName())
                .claim("userId", userId)
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

    public Authentication getAuthentication(String token) {
        Claims claims = getClaims(token);
        Long userId = claims.get("userId", Long.class);
        String username = claims.get("username", String.class);
        String email = claims.get("email", String.class);

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("authorities", String.class).split(","))
                        .map(SimpleGrantedAuthority::new)
                        .toList();

        CustomUserDetails userDetails = new CustomUserDetails(
                userId,
                username,
                null,
                authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(",")),
                email
        );

        return new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    }
}
