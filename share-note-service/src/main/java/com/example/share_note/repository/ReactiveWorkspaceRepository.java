package com.example.share_note.repository;

import com.example.share_note.domain.Workspace;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReactiveWorkspaceRepository extends ReactiveCrudRepository<Workspace, UUID> {
}
