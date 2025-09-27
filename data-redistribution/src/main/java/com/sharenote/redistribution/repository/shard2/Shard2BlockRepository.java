package com.sharenote.redistribution.repository.shard2;

import com.sharenote.redistribution.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface Shard2BlockRepository extends JpaRepository<Block, UUID> {

    /**
     * 페이지별 블록 개수 조회
     */
    @Query("SELECT COUNT(b) FROM Block b WHERE b.pageId = :pageId")
    long countByPageId(@Param("pageId") UUID pageId);

    /**
     * 페이지별 블록 삭제
     */
    @Modifying
    @Query("DELETE FROM Block b WHERE b.pageId = :pageId")
    int deleteByPageId(@Param("pageId") UUID pageId);
}
