package com.example.share_note.repository;

import com.example.share_note.domain.Workspace;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReactiveWorkspaceRepository extends ReactiveCrudRepository<Workspace, Long> {
}
