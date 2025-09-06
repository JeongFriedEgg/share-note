package com.example.share_note.repository;

import com.example.share_note.domain.WorkspaceMember;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveWorkspaceMemberRepository extends ReactiveCrudRepository<WorkspaceMember, Long> {
    Flux<WorkspaceMember> findByWorkspaceId(Long workspaceId);
    Mono<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
    Mono<Void> deleteByWorkspaceIdAndUserId(Long workspaceId, Long userId);
    Mono<Boolean> existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
