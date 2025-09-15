package com.example.share_note.repository;

import com.example.share_note.domain.Block;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ReactiveBlockRepository extends ReactiveCrudRepository<Block, UUID> {

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
    Mono<Void> updateArchiveStatusForPageTree(UUID pageId, boolean isArchived);

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
    Mono<Void> deleteAllByPageTree(UUID pageId);

    Mono<Block> findByIdAndPageId(UUID id, UUID pageId);

    Flux<Block> findAllByPageIdAndIsArchivedFalseOrderByPositionAsc(UUID pageId);


    /**
     * 주어진 블록과 모든 하위 블록의 isArchived 상태를 일괄적으로 업데이트합니다.
     * CTE(Common Table Expression)를 사용하여 계층 구조를 순회합니다.
     *
     */
    @Query("""
        WITH RECURSIVE block_tree AS (
            SELECT id
            FROM blocks
            WHERE id = :blockId

            UNION ALL

            SELECT b.id
            FROM blocks b
            JOIN block_tree bt ON b.parent_block_id = bt.id
        )
        UPDATE blocks
        SET
            is_archived = :isArchived,
            updated_at = NOW(),
            last_edited_by = :lastEditedBy
        WHERE id IN (SELECT id FROM block_tree);
    """)
    Mono<Integer> updateArchiveStatusForBlockTree(UUID blockId, boolean isArchived, UUID lastEditedBy);
}
