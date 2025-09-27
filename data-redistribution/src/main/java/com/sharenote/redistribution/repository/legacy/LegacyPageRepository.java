package com.sharenote.redistribution.repository.legacy;

import com.sharenote.redistribution.entity.Page;
import com.sharenote.redistribution.enums.MigrationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LegacyPageRepository extends JpaRepository<Page, UUID> {

    /**
     * 마이그레이션 상태별 페이지 조회 (수정날짜 오름차순)
     */
    @Query("SELECT p FROM Page p WHERE p.migrationStatus = :migrationStatus ORDER BY p.updatedAt ASC")
    org.springframework.data.domain.Page<Page> findByMigrationStatusOrderByUpdatedAtAsc(
            @Param("migrationStatus") MigrationStatus migrationStatus, Pageable pageable
    );

    /**
     * 특정 페이지의 마이그레이션 상태만 업데이트
     */
    @Modifying
    @Query("UPDATE Page p SET p.migrationStatus = :migrationStatus, p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :pageId")
    int updateMigrationStatusOnly(
            @Param("pageId") UUID pageId,
            @Param("migrationStatus") MigrationStatus migrationStatus
    );

    /**
     * 마이그레이션 상태별 페이지 개수 조회
     */
    @Query("SELECT COUNT(p) FROM Page p WHERE p.migrationStatus = :migrationStatus")
    long countByMigrationStatus(@Param("migrationStatus") MigrationStatus migrationStatus);

    @Modifying
    @Query("UPDATE Page p SET p.migrationStatus = 'READY' WHERE p.migrationStatus = 'MIGRATING'")
    void updateAllMigratingPagesToReady();
}
