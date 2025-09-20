package com.sharenote.redistribution.repository;

import com.sharenote.redistribution.entity.Page;
import com.sharenote.redistribution.enums.MigrationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PageRepository extends JpaRepository<Page, UUID> {

    /**
     * 마이그레이션 준비 상태인 페이지들을 페이징으로 조회
     * 인덱스 활용: idx_pages_workspace
     */
    @Query(value = """
        SELECT p FROM Page p 
        WHERE p.migrationStatus = :status 
        AND p.isArchived = false 
        ORDER BY p.createdAt ASC
        """)
    org.springframework.data.domain.Page<Page> findPagesByMigrationStatus(@Param("status") MigrationStatus status,
                                                                          Pageable pageable);

    /**
     * 워크스페이스별로 마이그레이션 준비 상태인 페이지들을 조회
     */
    @Query(value = """
        SELECT p FROM Page p 
        WHERE p.workspaceId = :workspaceId 
        AND p.migrationStatus = :status 
        AND p.isArchived = false 
        ORDER BY p.createdAt ASC
        """)
    List<Page> findByWorkspaceIdAndMigrationStatus(@Param("workspaceId") UUID workspaceId,
                                                   @Param("status") MigrationStatus status);

    /**
     * 페이지 ID 목록으로 조회
     */
    @Query("SELECT p FROM Page p WHERE p.id IN :pageIds")
    List<Page> findByIdIn(@Param("pageIds") List<UUID> pageIds);

    /**
     * 마이그레이션 상태 업데이트
     */
    @Modifying
    @Query("UPDATE Page p SET p.migrationStatus = :newStatus WHERE p.id = :pageId")
    int updateMigrationStatus(@Param("pageId") UUID pageId,
                              @Param("newStatus") MigrationStatus newStatus);

    /**
     * 마이그레이션 상태별 페이지 개수 조회
     */
    @Query("SELECT p.migrationStatus, COUNT(p) FROM Page p GROUP BY p.migrationStatus")
    List<Object[]> countByMigrationStatus();
}
