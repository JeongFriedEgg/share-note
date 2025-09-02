package com.example.share_note.service;

import com.example.share_note.dto.CustomUserDetails;
import com.example.share_note.repository.ReactiveUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CustomReactiveUserDetailsService implements ReactiveUserDetailsService {
    private final ReactiveUserRepository reactiveUserRepository;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return reactiveUserRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("User not found: "+username)))
                .map(user ->
                        new CustomUserDetails(
                                user.getUsername(),
                                user.getPassword(),
                                user.getAuthorities(),
                                user.getEmail()
                        )
                );
    }
}
