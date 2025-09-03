package com.example.share_note.repository;

import com.example.share_note.domain.RefreshToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveRefreshTokenRepository extends ReactiveCrudRepository<RefreshToken, Long> {
    Mono<Void> deleteByRefreshToken(String refreshToken);
}
