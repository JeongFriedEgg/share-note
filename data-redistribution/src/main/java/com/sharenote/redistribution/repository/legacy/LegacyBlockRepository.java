package com.sharenote.redistribution.repository.legacy;

import com.sharenote.redistribution.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LegacyBlockRepository extends JpaRepository<Block, UUID> {

    /**
     * 페이지별 블록 조회 (포지션 순서)
     */
    @Query("SELECT b FROM Block b WHERE b.pageId = :pageId ORDER BY b.position ASC")
    List<Block> findByPageIdOrderByPosition(@Param("pageId") UUID pageId);

    /**
     * 페이지별 블록 삭제
     */
    @Modifying
    @Query("DELETE FROM Block b WHERE b.pageId = :pageId")
    int deleteByPageId(@Param("pageId") UUID pageId);
}
