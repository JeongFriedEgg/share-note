package com.sharenote.redistribution.repository;

import com.sharenote.redistribution.entity.Block;
import com.sharenote.redistribution.enums.MigrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, UUID> {

    /**
     * 페이지별 블록 조회 (인덱스 활용: idx_blocks_page_id_is_archived_position)
     */
    @Query(value = """
        SELECT b FROM Block b 
        WHERE b.pageId = :pageId 
        AND b.isArchived = false 
        ORDER BY b.position ASC
        """)
    List<Block> findByPageIdAndNotArchived(@Param("pageId") UUID pageId);

    /**
     * 여러 페이지의 블록들을 한번에 조회
     */
    @Query(value = """
        SELECT b FROM Block b 
        WHERE b.pageId IN :pageIds 
        AND b.isArchived = false 
        ORDER BY b.pageId, b.position ASC
        """)
    List<Block> findByPageIdsAndNotArchived(@Param("pageIds") List<UUID> pageIds);

    /**
     * 페이지별 마이그레이션 상태 업데이트
     */
    @Modifying
    @Query("UPDATE Block b SET b.migrationStatus = :newStatus WHERE b.pageId = :pageId")
    int updateMigrationStatusByPageId(@Param("pageId") UUID pageId,
                                      @Param("newStatus") MigrationStatus newStatus);

    /**
     * 특정 페이지의 마이그레이션되지 않은 블록 개수 조회
     */
    @Query("SELECT COUNT(b) FROM Block b WHERE b.pageId = :pageId AND b.migrationStatus = :status")
    long countByPageIdAndMigrationStatus(@Param("pageId") UUID pageId,
                                         @Param("status") MigrationStatus status);
}
