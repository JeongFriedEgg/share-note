package com.sharenote.redistribution.service.migration;

import com.sharenote.redistribution.dto.response.MigrationProgressInfo;
import com.sharenote.redistribution.enums.MigrationStatus;
import com.sharenote.redistribution.repository.legacy.LegacyPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationProgressService {
    private final LegacyPageRepository legacyPageRepository;

    /**
     * 현재 마이그레이션 진행상황을 조회하는 메서드
     * Legacy DB 전용 읽기 전용 트랜잭션 사용
     */
    @Transactional(value = "legacyTransactionManager", readOnly = true)
    public MigrationProgressInfo getCurrentProgress() {
        try {
            long readyPages = legacyPageRepository.countByMigrationStatus(MigrationStatus.READY);
            long migratingPages = legacyPageRepository.countByMigrationStatus(MigrationStatus.MIGRATING);
            long migratedPages = legacyPageRepository.countByMigrationStatus(MigrationStatus.MIGRATED);
            long failedPages = legacyPageRepository.countByMigrationStatus(MigrationStatus.FAILED);

            long totalPages = readyPages + migratingPages + migratedPages + failedPages;

            MigrationProgressInfo progressInfo = MigrationProgressInfo.builder()
                    .totalPages(totalPages)
                    .readyPages(readyPages)
                    .migratingPages(migratingPages)
                    .migratedPages(migratedPages)
                    .failedPages(failedPages)
                    .build();

            log.debug("마이그레이션 진행상황 - 전체: {}, 완료: {}, 대기: {}, 진행중: {}, 실패: {}, 진행률: {:.2f}%",
                    totalPages, migratedPages, readyPages, migratingPages, failedPages,
                    progressInfo.getProgressPercentage());

            return progressInfo;

        } catch (Exception e) {
            log.error("마이그레이션 진행상황 조회 실패", e);
            // 실패 시 기본값 반환
            return MigrationProgressInfo.builder()
                    .totalPages(0)
                    .readyPages(0)
                    .migratingPages(0)
                    .migratedPages(0)
                    .failedPages(0)
                    .build();
        }
    }

    /**
     * 마이그레이션 완료되었는지 확인하는 메서드
     */
    public boolean isMigrationCompleted() {
        try {
            MigrationProgressInfo progressInfo = getCurrentProgress();
            return progressInfo.isCompleted();
        } catch (Exception e) {
            log.error("마이그레이션 완료 상태 확인 실패", e);
            return false;
        }
    }

    /**
     * 마이그레이션 진행률을 확인하는 메서드
     */
    public double getMigrationProgressPercentage() {
        try {
            MigrationProgressInfo progressInfo = getCurrentProgress();
            return progressInfo.getProgressPercentage();
        } catch (Exception e) {
            log.error("마이그레이션 진행률 확인 실패", e);
            return 0.0;
        }
    }

    /**
     * 50% 이상 마이그레이션 되었는지 확인하는 메서드
     */
    public boolean isMoreThanHalfCompleted() {
        try {
            MigrationProgressInfo progressInfo = getCurrentProgress();
            return progressInfo.isMoreThanHalfCompleted();
        } catch (Exception e) {
            log.error("마이그레이션 절반 완료 상태 확인 실패", e);
            return false;
        }
    }

    /**
     * 마이그레이션 상태를 로그로 출력하는 메서드
     */
    public void logMigrationStatus() {
        try {
            MigrationProgressInfo progressInfo = getCurrentProgress();

            log.info("=== 마이그레이션 진행상황 ===");
            log.info("전체 페이지 수: {}", progressInfo.getTotalPages());
            log.info("마이그레이션 완료: {} ({:.2f}%)",
                    progressInfo.getMigratedPages(),
                    progressInfo.getTotalPages() > 0 ?
                            (double) progressInfo.getMigratedPages() / progressInfo.getTotalPages() * 100 : 0);
            log.info("마이그레이션 대기: {}", progressInfo.getReadyPages());
            log.info("마이그레이션 진행중: {}", progressInfo.getMigratingPages());
            log.info("마이그레이션 실패: {}", progressInfo.getFailedPages());
            log.info("전체 진행률: {:.2f}%", progressInfo.getProgressPercentage());
            log.info("마이그레이션 완료 여부: {}", progressInfo.isCompleted() ? "완료" : "진행중");
            log.info("========================");

        } catch (Exception e) {
            log.error("마이그레이션 상태 로깅 실패", e);
            log.info("=== 마이그레이션 진행상황 (오류) ===");
            log.info("상태 조회 실패: {}", e.getMessage());
            log.info("========================");
        }
    }

}
