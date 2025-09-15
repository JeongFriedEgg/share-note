package com.example.share_note.repository;

import com.example.share_note.domain.WorkspaceMember;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ReactiveWorkspaceMemberRepository extends ReactiveCrudRepository<WorkspaceMember, UUID> {
    Flux<WorkspaceMember> findByWorkspaceId(UUID workspaceId);
    Mono<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    Mono<Void> deleteByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    Mono<Boolean> existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
