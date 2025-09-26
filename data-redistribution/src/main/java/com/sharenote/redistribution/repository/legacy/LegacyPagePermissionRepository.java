package com.sharenote.redistribution.repository.legacy;

import com.sharenote.redistribution.entity.PagePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LegacyPagePermissionRepository extends JpaRepository<PagePermission, UUID> {

    /**
     * 페이지별 권한 조회
     */
    @Query("SELECT pp FROM PagePermission pp WHERE pp.pageId = :pageId")
    List<PagePermission> findByPageId(@Param("pageId") UUID pageId);

    /**
     * 페이지별 권한 삭제
     */
    @Modifying
    @Query("DELETE FROM PagePermission pp WHERE pp.pageId = :pageId")
    int deleteByPageId(@Param("pageId") UUID pageId);
}
