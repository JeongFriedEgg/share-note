package com.example.share_note.repository;

import com.example.share_note.domain.Block;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveBlockRepository extends ReactiveCrudRepository<Block, Long> {

    // 재귀 CTE를 사용하여 특정 페이지와 모든 하위 페이지에 속한 블록의 is_archived 상태를 변경
    @Modifying
    @Query("""
        WITH RECURSIVE page_tree AS (
            SELECT id FROM page WHERE id = :pageId
            UNION ALL
            SELECT p.id FROM page p
            JOIN page_tree pt ON p.parent_page_id = pt.id
        )
        UPDATE block
        SET is_archived = :isArchived
        WHERE page_id IN (SELECT id FROM page_tree);
    """)
    Mono<Void> updateArchiveStatusForPageTree(Long pageId, boolean isArchived);

    // 재귀 CTE를 사용하여 특정 페이지와 모든 하위 페이지에 속한 블록을 영구 삭제
    @Modifying
    @Query("""
        WITH RECURSIVE page_tree AS (
            SELECT id FROM page WHERE id = :pageId
            UNION ALL
            SELECT p.id FROM page p
            JOIN page_tree pt ON p.parent_page_id = pt.id
        )
        DELETE FROM block
        WHERE page_id IN (SELECT id FROM page_tree);
    """)
    Mono<Void> deleteAllByPageTree(Long pageId);
}
