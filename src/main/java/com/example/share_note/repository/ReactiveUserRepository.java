package com.example.share_note.repository;

import com.example.share_note.domain.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveUserRepository extends ReactiveCrudRepository<User, Long> {
    Mono<User> findByUsername(String username);

    @Query("SELECT * FROM \"user\" WHERE username = :username OR email = :email LIMIT 1")
    Mono<User> findByUsernameOrEmail(String username, String email);
}
