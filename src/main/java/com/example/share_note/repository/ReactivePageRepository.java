package com.example.share_note.repository;

import com.example.share_note.domain.Page;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Repository
public interface ReactivePageRepository extends ReactiveCrudRepository<Page, Long> {

    @Query("SELECT * FROM pages WHERE id = :pageId AND workspace_id = :workspaceId")
    Mono<Page> findByIdAndWorkspaceId(Long pageId, Long workspaceId);


    Flux<Page> findAllByWorkspaceIdAndParentPageIdIsNull(Long workspaceId);

    // 단일 UPDATE 쿼리로 모든 페이지의 is_archived 상태를 변경하는 쿼리
    @Modifying
    @Query("""
        WITH RECURSIVE page_tree AS (
            SELECT id FROM page WHERE id = :pageId
            UNION ALL
            SELECT p.id FROM page p
            JOIN page_tree pt ON p.parent_page_id = pt.id
        )
        UPDATE page
        SET is_archived = :isArchived, updated_at = NOW(), last_edited_by = :userId
        WHERE id IN (SELECT id FROM page_tree);
    """)
    Mono<Void> updateArchiveStatusForTree(Long pageId, boolean isArchived, Long userId);

    // 재귀 CTE를 사용하여 모든 하위 페이지를 영구 삭제하는 쿼리
    @Modifying
    @Query("""
        WITH RECURSIVE page_tree AS (
            SELECT id FROM page WHERE id = :pageId
            UNION ALL
            SELECT p.id FROM page p
            JOIN page_tree pt ON p.parent_page_id = pt.id
        )
        DELETE FROM page WHERE id IN (SELECT id FROM page_tree);
    """)
    Mono<Void> deletePageAndDescendants(Long pageId);
}
