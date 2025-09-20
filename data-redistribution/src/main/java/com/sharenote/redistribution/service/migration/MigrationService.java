package com.sharenote.redistribution.service.migration;

import com.sharenote.redistribution.dto.response.BatchMigrationResult;
import com.sharenote.redistribution.dto.response.MigrationResult;
import com.sharenote.redistribution.entity.Block;
import com.sharenote.redistribution.entity.Page;
import com.sharenote.redistribution.entity.PagePermission;
import com.sharenote.redistribution.enums.MigrationStatus;
import com.sharenote.redistribution.repository.BlockRepository;
import com.sharenote.redistribution.repository.PagePermissionRepository;
import com.sharenote.redistribution.repository.PageRepository;
import com.sharenote.redistribution.repository.ShardAwareRepository;
import com.sharenote.redistribution.service.lock.DistributedLockService;
import com.sharenote.redistribution.service.shard.ShardStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MigrationService {

    private final PageRepository pageRepository;
    private final BlockRepository blockRepository;
    private final PagePermissionRepository pagePermissionRepository;
    private final ShardAwareRepository shardAwareRepository;
    private final ShardStrategy shardStrategy;
    private final DistributedLockService distributedLockService;
    private final MigrationProgressService progressService;

    @Value("${app.redistribution.batch-size:1000}")
    private int batchSize;

    @Value("${app.redistribution.delay-between-batches:1000}")
    private long delayBetweenBatches;

    @Value("${app.redistribution.retry-count:3}")
    private int retryCount;

    /**
     * 데이터 재분산 실행 메인 메서드
     */
    public MigrationResult executeMigration() {
        log.info("데이터 재분산 작업 시작 - 배치 크기: {}", batchSize);

        MigrationResult result = MigrationResult.builder()
                .startTime(LocalDateTime.now())
                .build();

        try {
            // 진행률 추적 초기화
            long totalPages = countPagesForMigration();
            progressService.initializeProgress(totalPages);

            result.setTotalPages(totalPages);
            log.info("마이그레이션 대상 페이지 총 {}개", totalPages);

            // 페이징 처리로 배치 마이그레이션 실행
            Pageable pageable = PageRequest.of(0, batchSize);
            org.springframework.data.domain.Page<Page> pagesBatch;
            int processedBatches = 0;

            do {
                pagesBatch = pageRepository.findPagesByMigrationStatus(
                        MigrationStatus.READY, pageable);

                if (pagesBatch.hasContent()) {
                    BatchMigrationResult batchResult = migrateBatch(pagesBatch.getContent());
                    result.addBatchResult(batchResult);

                    processedBatches++;
                    log.info("배치 {}개 완료 - 성공: {}, 실패: {}",
                            processedBatches, batchResult.getSuccessCount(), batchResult.getFailureCount());

                    // 배치 간 지연
                    if (delayBetweenBatches > 0 && pagesBatch.hasNext()) {
                        Thread.sleep(delayBetweenBatches);
                    }
                }

                pageable = pagesBatch.nextPageable();
            } while (pagesBatch.hasNext());

            result.setEndTime(LocalDateTime.now());
            result.setSuccess(true);

            log.info("데이터 재분산 완료 - 총 처리: {}개, 성공: {}개, 실패: {}개",
                    result.getTotalProcessed(), result.getTotalSuccess(), result.getTotalFailures());

        } catch (Exception e) {
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("데이터 재분산 작업 중 오류 발생", e);
        }

        return result;
    }

    /**
     * 배치 단위 마이그레이션 처리
     */
    private BatchMigrationResult migrateBatch(List<Page> pages) {
        BatchMigrationResult batchResult = new BatchMigrationResult();

        for (Page page : pages) {
            try {
                boolean migrated = migratePageWithRetry(page);
                if (migrated) {
                    batchResult.incrementSuccess();
                    progressService.incrementProgress();
                } else {
                    batchResult.incrementFailure();
                    batchResult.addFailedPageId(page.getId());
                }
            } catch (Exception e) {
                log.error("페이지 {} 마이그레이션 중 예상치 못한 오류", page.getId(), e);
                batchResult.incrementFailure();
                batchResult.addFailedPageId(page.getId());
            }
        }

        return batchResult;
    }

    /**
     * 재시도를 포함한 페이지 마이그레이션
     */
    private boolean migratePageWithRetry(Page page) {
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                return distributedLockService.executeWithLock(page.getId(), () -> {
                    return migratePage(page);
                });
            } catch (Exception e) {
                log.warn("페이지 {} 마이그레이션 시도 {}/{} 실패: {}",
                        page.getId(), attempt, retryCount, e.getMessage());

                if (attempt == retryCount) {
                    log.error("페이지 {} 마이그레이션 최대 재시도 횟수 초과", page.getId(), e);
                    return false;
                }

                // 재시도 전 대기
                try {
                    Thread.sleep(1000 * attempt); // 지수 백오프
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 단일 페이지 마이그레이션 (분산락 내에서 실행)
     */
    @Transactional
    protected boolean migratePage(Page page) {
        try {
            // 1. 마이그레이션 상태를 MIGRATING으로 변경
            updatePageMigrationStatus(page.getId(), MigrationStatus.MIGRATING);

            // 2. 대상 샤드 결정
            String targetShard = shardStrategy.determineShardByPageId(page.getId());
            log.debug("페이지 {} 대상 샤드: {}", page.getId(), targetShard);

            // 3. 관련 데이터 조회
            List<Block> blocks = blockRepository.findByPageIdAndNotArchived(page.getId());
            List<PagePermission> permissions = pagePermissionRepository.findByPageId(page.getId());

            // 4. 대상 샤드에 데이터 저장
            savePageDataToTargetShard(targetShard, page, blocks, permissions);

            // 5. 마이그레이션 상태를 MIGRATED로 변경
            updateAllMigrationStatus(page.getId(), MigrationStatus.MIGRATED);

            log.info("페이지 {} 마이그레이션 완료 - 샤드: {}, 블록: {}개, 권한: {}개",
                    page.getId(), targetShard, blocks.size(), permissions.size());

            return true;

        } catch (Exception e) {
            log.error("페이지 {} 마이그레이션 실패", page.getId(), e);
            // 실패 시 상태를 READY로 롤백
            try {
                updateAllMigrationStatus(page.getId(), MigrationStatus.READY);
            } catch (Exception rollbackException) {
                log.error("페이지 {} 마이그레이션 상태 롤백 실패", page.getId(), rollbackException);
            }
            throw e;
        }
    }

    /**
     * 대상 샤드에 페이지 관련 데이터 저장
     */
    private void savePageDataToTargetShard(String targetShard, Page page,
                                           List<Block> blocks, List<PagePermission> permissions) {

        PlatformTransactionManager targetTxManager = shardAwareRepository.getTransactionManager(targetShard);

        // 대상 샤드에서 새 트랜잭션 시작
        TransactionStatus targetTxStatus = targetTxManager.getTransaction(
                new DefaultTransactionDefinition());

        try {
            // EntityManager 생성
            try (var entityManager = shardAwareRepository.getEntityManager(targetShard)) {

                // 페이지 저장
                shardAwareRepository.savePageToShard(targetShard, page, entityManager);

                // 블록들 저장
                for (Block block : blocks) {
                    shardAwareRepository.saveBlockToShard(targetShard, block, entityManager);
                }

                // 권한들 저장
                for (PagePermission permission : permissions) {
                    shardAwareRepository.savePagePermissionToShard(targetShard, permission, entityManager);
                }

                // 배치 처리를 위한 flush
                shardAwareRepository.flushAndClearShard(targetShard, entityManager);
            }

            // 트랜잭션 커밋
            targetTxManager.commit(targetTxStatus);

        } catch (Exception e) {
            // 오류 발생 시 롤백
            targetTxManager.rollback(targetTxStatus);
            throw new RuntimeException("대상 샤드 " + targetShard + "에 데이터 저장 실패", e);
        }
    }


    /**
     * 페이지 마이그레이션 상태 업데이트
     */
    private void updatePageMigrationStatus(UUID pageId, MigrationStatus status) {
        int updated = pageRepository.updateMigrationStatus(pageId, status);
        if (updated == 0) {
            throw new RuntimeException("페이지 " + pageId + " 마이그레이션 상태 업데이트 실패");
        }
    }

    /**
     * 페이지 관련 모든 데이터의 마이그레이션 상태 업데이트
     */
    private void updateAllMigrationStatus(UUID pageId, MigrationStatus status) {
        updatePageMigrationStatus(pageId, status);
        blockRepository.updateMigrationStatusByPageId(pageId, status);
        pagePermissionRepository.updateMigrationStatusByPageId(pageId, status);
    }

    /**
     * 마이그레이션 대상 페이지 수 조회
     */
    private long countPagesForMigration() {
        return pageRepository.findPagesByMigrationStatus(
                MigrationStatus.READY, Pageable.unpaged()).getTotalElements();
    }
}