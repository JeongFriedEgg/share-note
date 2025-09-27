package com.sharenote.redistribution.service.migration;

import com.sharenote.redistribution.entity.Block;
import com.sharenote.redistribution.entity.Page;
import com.sharenote.redistribution.entity.PagePermission;
import com.sharenote.redistribution.enums.MigrationStatus;
import com.sharenote.redistribution.exception.custom.MigrationException;
import com.sharenote.redistribution.repository.legacy.LegacyBlockRepository;
import com.sharenote.redistribution.repository.legacy.LegacyPagePermissionRepository;
import com.sharenote.redistribution.repository.legacy.LegacyPageRepository;
import com.sharenote.redistribution.repository.shard1.Shard1BlockRepository;
import com.sharenote.redistribution.repository.shard1.Shard1PagePermissionRepository;
import com.sharenote.redistribution.repository.shard1.Shard1PageRepository;
import com.sharenote.redistribution.repository.shard2.Shard2BlockRepository;
import com.sharenote.redistribution.repository.shard2.Shard2PagePermissionRepository;
import com.sharenote.redistribution.repository.shard2.Shard2PageRepository;
import com.sharenote.redistribution.service.migration.vo.MigrationDataVo;
import com.sharenote.redistribution.service.shard.ShardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationTransactionService {
    private final LegacyPageRepository legacyPageRepository;
    private final LegacyBlockRepository legacyBlockRepository;
    private final LegacyPagePermissionRepository legacyPagePermissionRepository;

    private final Shard1PageRepository shard1PageRepository;
    private final Shard1BlockRepository shard1BlockRepository;
    private final Shard1PagePermissionRepository shard1PagePermissionRepository;

    private final Shard2PageRepository shard2PageRepository;
    private final Shard2BlockRepository shard2BlockRepository;
    private final Shard2PagePermissionRepository shard2PagePermissionRepository;

    private final ShardService shardService;

    private static final String LEGACY_SHARD_KEY = "legacy";


    /**
     * 실제 마이그레이션 로직 (단일 트랜잭션으로 처리)
     */
    @Transactional("legacyTransactionManager")
    public void performMigrationTransaction(UUID pageId) {
        // 1. 마이그레이션 상태를 MIGRATING으로 변경
        int updatedCount = legacyPageRepository.updateMigrationStatusOnly(pageId, MigrationStatus.MIGRATING);
        if (updatedCount == 0) {
            throw new MigrationException("페이지 상태 업데이트 실패: " + pageId);
        }
        log.debug("페이지 {} 상태를 MIGRATING으로 변경", pageId);

        // 2. 대상 샤드 결정
        String targetShard = shardService.determineTargetShard(pageId);
        log.info("페이지 {} 마이그레이션 시작 - 대상 샤드: {}", pageId, targetShard);

        // 3. Legacy 샤드인 경우 상태만 변경
        if (LEGACY_SHARD_KEY.equals(targetShard)) {
            legacyPageRepository.updateMigrationStatusOnly(pageId, MigrationStatus.MIGRATED);
            log.info("페이지 {} Legacy 샤드 마이그레이션 완료 (상태 변경만)", pageId);
            return;
        }

        // 4. Legacy에서 데이터 조회
        MigrationDataVo migrationData = loadPageDataFromLegacy(pageId);
        log.debug("Legacy 데이터 조회 완료 - 페이지: {}, 블록: {}개, 권한: {}개",
                pageId, migrationData.getBlocks().size(), migrationData.getPermissions().size());

        // 5. 대상 샤드로 데이터 복제 (별도 트랜잭션)
        replicatePageDataToTargetShard(migrationData, targetShard);

        // 6. 데이터 검증 (별도 트랜잭션)
        validateMigratedData(migrationData, targetShard);

        // 7. Legacy DB에서 데이터 삭제
        deletePageDataFromLegacy(pageId, migrationData);

        log.info("페이지 {} 마이그레이션 완료", pageId);
    }

    /**
     * 페이지의 마이그레이션 상태를 업데이트 - Legacy DB 전용 트랜잭션
     */
    @Transactional("legacyTransactionManager")
    public void updatePageMigrationStatus(UUID pageId, MigrationStatus migrationStatus) {
        try {
            int updatedCount = legacyPageRepository.updateMigrationStatusOnly(pageId, migrationStatus);
            if (updatedCount == 0) {
                throw new MigrationException("페이지 상태 업데이트 실패: " + pageId);
            }
            log.debug("페이지 {} 상태를 {}로 변경", pageId, migrationStatus);
        } catch (Exception e) {
            throw new MigrationException("페이지 상태 업데이트 중 오류: " + pageId, e);
        }
    }

    /**
     * Legacy에서 페이지 관련 모든 데이터를 조회 - Legacy DB 전용 트랜잭션
     */
    public MigrationDataVo loadPageDataFromLegacy(UUID pageId) {
        try {
            // 페이지 데이터 조회
            Page page = legacyPageRepository.findById(pageId)
                    .orElseThrow(() -> new MigrationException("페이지를 찾을 수 없습니다: " + pageId));

            // 마이그레이션 상태 재확인
            if (!MigrationStatus.MIGRATING.equals(page.getMigrationStatus())) {
                throw new MigrationException("페이지 상태가 MIGRATING이 아닙니다: " + pageId);
            }

            // 블록 데이터 조회
            List<Block> blocks = legacyBlockRepository.findByPageIdOrderByPosition(pageId);

            // 권한 데이터 조회
            List<PagePermission> permissions = legacyPagePermissionRepository.findByPageId(pageId);

            log.debug("Legacy에서 데이터 조회 완료 - 페이지: {}, 블록: {}개, 권한: {}개",
                    pageId, blocks.size(), permissions.size());

            return MigrationDataVo.builder()
                    .page(page)
                    .blocks(blocks)
                    .permissions(permissions)
                    .build();

        } catch (Exception e) {
            throw new MigrationException("Legacy 데이터 조회 실패: " + pageId, e);
        }
    }

    /**
     * Legacy DB에서 페이지 관련 데이터를 안전하게 삭제 - Legacy DB 전용 트랜잭션
     */
    public void deletePageDataFromLegacy(UUID pageId, MigrationDataVo originalData) {
        try {
            // 1. 권한 삭제 (FK 제약조건으로 인해 먼저)
            int deletedPermissions = legacyPagePermissionRepository.deleteByPageId(pageId);
            log.debug("삭제된 권한 수: {} (예상: {})", deletedPermissions, originalData.getPermissions().size());

            // 2. 블록 삭제 (FK 제약조건으로 인해 먼저)
            int deletedBlocks = legacyBlockRepository.deleteByPageId(pageId);
            log.debug("삭제된 블록 수: {} (예상: {})", deletedBlocks, originalData.getBlocks().size());

            // 3. 페이지 삭제
            legacyPageRepository.deleteById(pageId);

            // 4. 삭제 검증
            if (legacyPageRepository.existsById(pageId)) {
                throw new MigrationException("페이지 삭제 실패: " + pageId);
            }

            log.debug("Legacy 데이터 삭제 완료: 페이지={}, 블록={}, 권한={}",
                    pageId, deletedBlocks, deletedPermissions);

        } catch (Exception e) {
            throw new MigrationException("Legacy DB에서 데이터 삭제 실패: " + pageId, e);
        }
    }

    /**
     * 대상 샤드로 데이터를 복제
     */
    public void replicatePageDataToTargetShard(MigrationDataVo migrationData, String targetShard) {
        UUID pageId = migrationData.getPage().getId();

        try {
            if ("shard1".equals(targetShard)) {
                replicateToShard1(migrationData);
            } else if ("shard2".equals(targetShard)) {
                replicateToShard2(migrationData);
            } else {
                throw new MigrationException("알 수 없는 대상 샤드: " + targetShard);
            }

            log.debug("페이지 {} 데이터 복제 완료: {}", pageId, targetShard);

        } catch (Exception e) {
            throw new MigrationException("대상 샤드 " + targetShard + "로 데이터 복제 실패", e);
        }
    }

    /**
     * 마이그레이션된 데이터 검증
     */
    public void validateMigratedData(MigrationDataVo originalData, String targetShard) {
        try {
            UUID pageId = originalData.getPageId();

            if ("shard1".equals(targetShard)) {
                validateShard1Data(originalData);
            } else if ("shard2".equals(targetShard)) {
                validateShard2Data(originalData);
            } else {
                throw new MigrationException("알 수 없는 대상 샤드: " + targetShard);
            }

            log.debug("데이터 검증 완료: {}", pageId);

        } catch (Exception e) {
            throw new MigrationException("데이터 검증 실패: " + originalData.getPageId(), e);
        }
    }

    /**
     * Shard1 데이터 검증 - Shard1 읽기 전용 트랜잭션
     */
    @Transactional(value = "shard1TransactionManager", readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void validateShard1Data(MigrationDataVo originalData) {
        UUID pageId = originalData.getPageId();

        // 1. 페이지 존재 및 속성 검증
        Page migratedPage = shard1PageRepository.findById(pageId)
                .orElseThrow(() -> new MigrationException("마이그레이션된 페이지를 찾을 수 없습니다: " + pageId));

        if (!migratedPage.getTitle().equals(originalData.getPage().getTitle())) {
            throw new MigrationException("페이지 제목 불일치: " + pageId);
        }

        // 2. 블록 수 검증
        long migratedBlockCount = shard1BlockRepository.countByPageId(pageId);
        if (migratedBlockCount != originalData.getBlocks().size()) {
            throw new MigrationException(String.format(
                    "블록 개수 불일치: 원본=%d, 마이그레이션=%d, 페이지=%s",
                    originalData.getBlocks().size(), migratedBlockCount, pageId));
        }

        // 3. 권한 수 검증
        long migratedPermissionCount = shard1PagePermissionRepository.countByPageId(pageId);
        if (migratedPermissionCount != originalData.getPermissions().size()) {
            throw new MigrationException(String.format(
                    "권한 개수 불일치: 원본=%d, 마이그레이션=%d, 페이지=%s",
                    originalData.getPermissions().size(), migratedPermissionCount, pageId));
        }

        log.debug("Shard1 데이터 검증 완료 - 페이지: {}, 블록: {}, 권한: {}",
                pageId, migratedBlockCount, migratedPermissionCount);
    }

    /**
     * Shard2 데이터 검증 - Shard2 읽기 전용 트랜잭션
     */
    @Transactional(value = "shard2TransactionManager", readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void validateShard2Data(MigrationDataVo originalData) {
        UUID pageId = originalData.getPageId();

        // 1. 페이지 존재 및 속성 검증
        Page migratedPage = shard2PageRepository.findById(pageId)
                .orElseThrow(() -> new MigrationException("마이그레이션된 페이지를 찾을 수 없습니다: " + pageId));

        if (!migratedPage.getTitle().equals(originalData.getPage().getTitle())) {
            throw new MigrationException("페이지 제목 불일치: " + pageId);
        }

        // 2. 블록 수 검증
        long migratedBlockCount = shard2BlockRepository.countByPageId(pageId);
        if (migratedBlockCount != originalData.getBlocks().size()) {
            throw new MigrationException(String.format(
                    "블록 개수 불일치: 원본=%d, 마이그레이션=%d, 페이지=%s",
                    originalData.getBlocks().size(), migratedBlockCount, pageId));
        }

        // 3. 권한 수 검증
        long migratedPermissionCount = shard2PagePermissionRepository.countByPageId(pageId);
        if (migratedPermissionCount != originalData.getPermissions().size()) {
            throw new MigrationException(String.format(
                    "권한 개수 불일치: 원본=%d, 마이그레이션=%d, 페이지=%s",
                    originalData.getPermissions().size(), migratedPermissionCount, pageId));
        }

        log.debug("Shard2 데이터 검증 완료 - 페이지: {}, 블록: {}, 권한: {}",
                pageId, migratedBlockCount, migratedPermissionCount);
    }

    /**
     * Shard1으로 데이터 복제 - Shard1 트랜잭션
     */
    @Transactional(value = "shard1TransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void replicateToShard1(MigrationDataVo migrationData) {
        UUID pageId = migrationData.getPage().getId();

        // 중복 체크 후 삭제
        if (shard1PageRepository.existsById(pageId)) {
            log.warn("페이지 {}가 이미 Shard1에 존재합니다. 기존 데이터를 삭제합니다.", pageId);
            shard1PagePermissionRepository.deleteByPageId(pageId);
            shard1BlockRepository.deleteByPageId(pageId);
            shard1PageRepository.deleteById(pageId);
        }

        // 1. 페이지 복제
        Page savedPage = shard1PageRepository.save(migrationData.getPage());
        log.debug("Shard1 페이지 {} 복제 완료", savedPage.getId());

        // 2. 블록들 복제
        if (!migrationData.getBlocks().isEmpty()) {
            List<Block> savedBlocks = shard1BlockRepository.saveAll(migrationData.getBlocks());
            log.debug("Shard1 블록 {}개 복제 완료", savedBlocks.size());
        }

        // 3. 권한들 복제
        if (!migrationData.getPermissions().isEmpty()) {
            List<PagePermission> savedPermissions = shard1PagePermissionRepository.saveAll(migrationData.getPermissions());
            log.debug("Shard1 권한 {}개 복제 완료", savedPermissions.size());
        }
    }

    /**
     * Shard2로 데이터 복제 - Shard2 트랜잭션
     */
    @Transactional(value = "shard2TransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void replicateToShard2(MigrationDataVo migrationData) {
        UUID pageId = migrationData.getPage().getId();

        // 중복 체크 후 삭제
        if (shard2PageRepository.existsById(pageId)) {
            log.warn("페이지 {}가 이미 Shard2에 존재합니다. 기존 데이터를 삭제합니다.", pageId);
            shard2PagePermissionRepository.deleteByPageId(pageId);
            shard2BlockRepository.deleteByPageId(pageId);
            shard2PageRepository.deleteById(pageId);
        }

        // 1. 페이지 복제
        Page savedPage = shard2PageRepository.save(migrationData.getPage());
        log.debug("Shard2 페이지 {} 복제 완료", savedPage.getId());

        // 2. 블록들 복제
        if (!migrationData.getBlocks().isEmpty()) {
            List<Block> savedBlocks = shard2BlockRepository.saveAll(migrationData.getBlocks());
            log.debug("Shard2 블록 {}개 복제 완료", savedBlocks.size());
        }

        // 3. 권한들 복제
        if (!migrationData.getPermissions().isEmpty()) {
            List<PagePermission> savedPermissions = shard2PagePermissionRepository.saveAll(migrationData.getPermissions());
            log.debug("Shard2 권한 {}개 복제 완료", savedPermissions.size());
        }
    }

    /**
     * Shard1 데이터 롤백 - Shard1 트랜잭션
     */
    @Transactional(value = "shard1TransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void rollbackShard1Data(UUID pageId) {
        if (shard1PageRepository.existsById(pageId)) {
            shard1PagePermissionRepository.deleteByPageId(pageId);
            shard1BlockRepository.deleteByPageId(pageId);
            shard1PageRepository.deleteById(pageId);
            log.info("Shard1에서 롤백 데이터 삭제 완료: {}", pageId);
        }
    }

    /**
     * Shard2 데이터 롤백 - Shard2 트랜잭션
     */
    @Transactional(value = "shard2TransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void rollbackShard2Data(UUID pageId) {
        if (shard2PageRepository.existsById(pageId)) {
            shard2PagePermissionRepository.deleteByPageId(pageId);
            shard2BlockRepository.deleteByPageId(pageId);
            shard2PageRepository.deleteById(pageId);
            log.info("Shard2에서 롤백 데이터 삭제 완료: {}", pageId);
        }
    }

    /**
     * Legacy DB 상태 롤백 - Legacy DB 전용 트랜잭션
     */
    @Transactional(value = "legacyTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void rollbackLegacyStatus(UUID pageId) {
        try {
            if (legacyPageRepository.existsById(pageId)) {
                updatePageMigrationStatus(pageId, MigrationStatus.READY);
                log.info("Legacy에서 마이그레이션 상태 READY로 복원 완료: {}", pageId);
            }
        } catch (Exception e) {
            log.error("Legacy에서 마이그레이션 상태 복원 실패: {}", pageId, e);
        }
    }

    /**
     * 페이지를 마이그레이션 실패 상태로 마킹 - Legacy DB 전용 트랜잭션
     */
    @Transactional(value = "legacyTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void markPageStatusAsFailed(UUID pageId) {
        try {
            if (legacyPageRepository.existsById(pageId)) {
                updatePageMigrationStatus(pageId, MigrationStatus.FAILED);
                log.warn("페이지 {} 마이그레이션 상태를 FAILED로 변경", pageId);
            }
        } catch (Exception e) {
            log.error("페이지 {} 실패 상태 마킹 중 오류 발생", pageId, e);
        }
    }
}
