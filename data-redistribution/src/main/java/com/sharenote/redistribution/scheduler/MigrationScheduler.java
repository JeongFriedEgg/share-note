package com.sharenote.redistribution.scheduler;

import com.sharenote.redistribution.service.migration.MigrationProgressService;
import com.sharenote.redistribution.service.migration.MigrationService;
import com.sharenote.redistribution.service.migration.MigrationTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
public class MigrationScheduler {
    private final MigrationService migrationService;
    private final MigrationProgressService migrationProgressService;
    private final MigrationTransactionService migrationTransactionService;

    private volatile boolean migrationRunning = false;
    private final Object migrationLock = new Object();

    /**
     * 주기적으로 마이그레이션을 실행하는 스케줄러
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    public void scheduledMigration() {
        synchronized (migrationLock) {
            if (migrationRunning) {
                log.info("마이그레이션이 이미 실행 중이므로 스케줄된 실행을 건너뜁니다.");
                return;
            }

            // 마이그레이션 완료 확인
            if (migrationProgressService.isMigrationCompleted()) {
                log.info("모든 페이지 마이그레이션이 완료되었습니다. 스케줄 실행을 중단합니다.");
                return;
            }

            migrationRunning = true;
        }

        try {
            log.info("스케줄된 마이그레이션을 시작합니다.");
            migrationProgressService.logMigrationStatus();

            migrationService.resetMigratingStatus();
            migrationService.executeMigration();

            log.info("스케줄된 마이그레이션을 완료했습니다.");
            migrationProgressService.logMigrationStatus();
        } catch (Exception e) {
            log.error("스케줄된 마이그레이션 중 오류가 발생했습니다.",e);
        }finally {
            synchronized (migrationLock) {
                migrationRunning = false;
            }
        }
    }
}
