package com.sharenote.redistribution.service.migration;

import com.sharenote.redistribution.entity.Page;
import com.sharenote.redistribution.enums.MigrationStatus;
import com.sharenote.redistribution.exception.custom.LockAcquisitionException;
import com.sharenote.redistribution.exception.custom.MigrationException;
import com.sharenote.redistribution.exception.custom.RedisConnectionException;
import com.sharenote.redistribution.properties.MigrationProperties;
import com.sharenote.redistribution.repository.legacy.LegacyPageRepository;
import com.sharenote.redistribution.service.lock.DistributedLockService;
import com.sharenote.redistribution.service.shard.ShardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {
    private final LegacyPageRepository legacyPageRepository;

    private final DistributedLockService distributedLockService;
    private final MigrationTransactionService migrationTransactionService;
    private final ShardService shardService;
    private final MigrationProperties migrationProperties;

    private static final String LEGACY_SHARD_KEY = "legacy";

    /**
     * 이전 마이그레이션 실패 페이지 상태를 재설정 - 개선된 버전
     */
    @Transactional(value = "legacyTransactionManager", propagation = Propagation.REQUIRED)
    public void resetMigratingStatus() {
        log.info("이전 마이그레이션 실패 페이지 상태를 재설정합니다.");
        legacyPageRepository.updateAllMigratingPagesToReady();
        log.info("MIGRATING 상태 페이지 {}개를 READY로 재설정 완료.");
    }

    /**
     * 메인 마이그레이션 실행 메서드
     */
    public void executeMigration() {
        log.info("페이지 마이그레이션을 시작합니다.");

        int processedBatchCount = 0;
        int totalProcessedPages = 0;
        int totalFailedPages = 0;
        int lockFailureCount = 0;

        while (true) {
            List<Page> pagesToMigrate = getNextBatchOfPagesToMigrate();

            if (pagesToMigrate.isEmpty()) {
                log.info("마이그레이션할 페이지가 더 이상 없습니다. 마이그레이션을 완료합니다.");
                break;
            }

            log.info("배치 #{} 시작 - {}개의 페이지를 처리합니다.", ++processedBatchCount, pagesToMigrate.size());

            // 배치 내의 각 페이지를 순차적으로 마이그레이션
            for (Page page : pagesToMigrate) {
                try {
                    migratePageWithRetry(page);
                    totalProcessedPages++;
                    log.debug("페이지 {} 마이그레이션 완료", page.getId());
                } catch (RedisConnectionException e) {
                    log.error("Redis 연결 실패로 인한 마이그레이션 중단: 페이지 {}", page.getId(), e);
                    throw new MigrationException("Redis 연결 실패로 인한 전체 마이그레이션 중단", e);

                } catch (LockAcquisitionException e) {
                    lockFailureCount++;
                    totalFailedPages++;
                    log.error("분산락 획득 실패: 페이지 {} - 다른 프로세스에서 처리 중일 수 있습니다", page.getId(), e);

                    // 락 획득 실패는 READY 상태로 되돌려서 나중에 재시도 가능하도록 함
                    migrationTransactionService.rollbackLegacyStatus(page.getId());

                } catch (Exception e) {
                    totalFailedPages++;
                    log.error("페이지 {} 마이그레이션 최종 실패", page.getId(), e);
                }
            }

            log.info("배치 #{} 완료 - 성공: {}, 실패: {} (락실패: {}), 누적 처리: {}",
                    processedBatchCount,
                    totalProcessedPages - totalFailedPages,
                    totalFailedPages,
                    lockFailureCount,
                    totalProcessedPages);

            if (lockFailureCount > pagesToMigrate.size() / 2) {
                log.warn("락 실패가 많습니다. 5초간 대기 후 계속 진행합니다.");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new MigrationException("마이그레이션 중 인터럽트 발생", ie);
                }
                lockFailureCount = 0; // 리셋
            }
        }

        log.info("전체 마이그레이션 완료 - 총 성공: {}, 총 실패: {}",
                totalProcessedPages - totalFailedPages, totalFailedPages);
    }

    /**
     * 마이그레이션이 필요한 다음 배치의 페이지들을 조회
     */
    @Transactional(value = "legacyTransactionManager", readOnly = true)
    private List<Page> getNextBatchOfPagesToMigrate() {
        Pageable pageable = PageRequest.of(0, migrationProperties.getBatchSize());

        // READY 상태 페이지 우선 조회
        org.springframework.data.domain.Page<Page> readyPages =
                legacyPageRepository.findByMigrationStatusOrderByUpdatedAtAsc(MigrationStatus.READY, pageable);

        if (!readyPages.getContent().isEmpty()) {
            return readyPages.getContent();
        }

        // READY가 없으면 FAILED 상태 재시도
        org.springframework.data.domain.Page<Page> failedPages =
                legacyPageRepository.findByMigrationStatusOrderByUpdatedAtAsc(MigrationStatus.FAILED, pageable);

        return failedPages.getContent();
    }

    /**
     * 재시도 로직이 포함된 페이지 마이그레이션 메서드
     */
    private void migratePageWithRetry(Page page) {
        int attemptCount = 0;
        UUID pageId = page.getId();
        int retryCount = migrationProperties.getRetryCount();
        Exception lastException = null;

        while (attemptCount < retryCount) {
            attemptCount++;

            try {
                migrateSinglePage(page);
                log.info("페이지 {} 마이그레이션 성공 (시도 {}/{})", pageId, attemptCount, retryCount);
                return;
            } catch (RedisConnectionException | LockAcquisitionException e) {
                // Redis 관련 예외는 즉시 상위로 전파 (재시도 하지 않음)
                log.error("분산락 문제로 페이지 {} 마이그레이션 실패 (시도 {}/{})",
                        pageId, attemptCount, retryCount, e);
                throw e;
            }catch (Exception e) {
                lastException = e;
                log.warn("페이지 {} 마이그레이션 실패 (시도 {}/{}) - {}",
                        pageId, attemptCount, retryCount, e.getMessage());

                if (attemptCount < retryCount) {
                    try {
                        Thread.sleep(1000 + (attemptCount * 500));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new MigrationException("마이그레이션 재시도 중 인터럽트 발생", ie);
                    }
                }
            }
        }

        // 최대 재시도 횟수 초과 시 실패 처리
        log.error("페이지 {} 마이그레이션 최대 재시도 횟수 초과", pageId);
        migrationTransactionService.markPageStatusAsFailed(pageId);
        throw new MigrationException("페이지 마이그레이션 최종 실패: " + pageId, lastException);
    }

    /**
     * 단일 페이지 마이그레이션 메서드 (트랜잭션 없음, 분산락 사용)
     */
    public void migrateSinglePage(Page page) {
        UUID pageId = page.getId();
        String lockKey = "migration:page:" + pageId;

        distributedLockService.executeWithLock(lockKey, 300, 600, () -> {
            try {
                // 전체 마이그레이션 로직을 트랜잭션 메서드로 위임
                migrationTransactionService.performMigrationTransaction(pageId);
                return null;

            } catch (Exception e) {
                log.error("페이지 {} 마이그레이션 중 오류 발생", pageId, e);
                // 롤백은 트랜잭션 밖에서 처리
                rollbackPageMigration(pageId);
                throw e;
            }
        });
    }

    /**
     * 마이그레이션 롤백 처리
     */
    public void rollbackPageMigration(UUID pageId) {
        log.warn("페이지 {} 마이그레이션 롤백 시작", pageId);

        try {
            String targetShard = shardService.determineTargetShard(pageId);

            // 1. 대상 샤드에서 복제된 데이터 삭제
            if (!LEGACY_SHARD_KEY.equals(targetShard)) {
                if ("shard1".equals(targetShard)) {
                    migrationTransactionService.rollbackShard1Data(pageId); // 프록시 호출
                } else if ("shard2".equals(targetShard)) {
                    migrationTransactionService.rollbackShard2Data(pageId); // 프록시 호출
                }
            }

            // 2. Legacy에서 상태를 READY로 복원
            migrationTransactionService.rollbackLegacyStatus(pageId);// 프록시 호출

        } catch (Exception e) {
            log.error("페이지 {} 마이그레이션 롤백 중 오류 발생", pageId, e);
        }
    }
}
