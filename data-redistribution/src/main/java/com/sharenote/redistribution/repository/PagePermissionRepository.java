package com.sharenote.redistribution.repository;

import com.sharenote.redistribution.entity.PagePermission;
import com.sharenote.redistribution.enums.MigrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PagePermissionRepository extends JpaRepository<PagePermission, UUID> {

    /**
     * 페이지별 권한 조회 (인덱스 활용: idx_page_permissions_page_id_user_id)
     */
    @Query("SELECT pp FROM PagePermission pp WHERE pp.pageId = :pageId")
    List<PagePermission> findByPageId(@Param("pageId") UUID pageId);

    /**
     * 여러 페이지의 권한들을 한번에 조회
     */
    @Query("SELECT pp FROM PagePermission pp WHERE pp.pageId IN :pageIds ORDER BY pp.pageId")
    List<PagePermission> findByPageIds(@Param("pageIds") List<UUID> pageIds);

    /**
     * 페이지별 마이그레이션 상태 업데이트
     */
    @Modifying
    @Query("UPDATE PagePermission pp SET pp.migrationStatus = :newStatus WHERE pp.pageId = :pageId")
    int updateMigrationStatusByPageId(@Param("pageId") UUID pageId,
                                      @Param("newStatus") MigrationStatus newStatus);

    /**
     * 특정 페이지의 마이그레이션되지 않은 권한 개수 조회
     */
    @Query("SELECT COUNT(pp) FROM PagePermission pp WHERE pp.pageId = :pageId AND pp.migrationStatus = :status")
    long countByPageIdAndMigrationStatus(@Param("pageId") UUID pageId,
                                         @Param("status") MigrationStatus status);
}