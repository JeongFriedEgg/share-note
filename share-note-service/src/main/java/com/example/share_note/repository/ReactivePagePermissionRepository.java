package com.example.share_note.repository;

import com.example.share_note.domain.PagePermission;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;


@Repository
public interface ReactivePagePermissionRepository extends ReactiveCrudRepository<PagePermission, UUID> {
    Mono<PagePermission> findByPageIdAndUserId(UUID pageId, UUID userId);
}
