package com.example.share_note.repository;

import com.example.share_note.domain.RefreshToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ReactiveRefreshTokenRepository extends ReactiveCrudRepository<RefreshToken, UUID> {
    Mono<Void> deleteByRefreshToken(String refreshToken);
}
