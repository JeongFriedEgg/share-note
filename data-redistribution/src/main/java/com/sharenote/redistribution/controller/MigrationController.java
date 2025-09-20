package com.sharenote.redistribution.controller;

import com.sharenote.redistribution.dto.response.ApiResponse;
import com.sharenote.redistribution.dto.response.MigrationResult;
import com.sharenote.redistribution.service.migration.MigrationProgressService;
import com.sharenote.redistribution.service.migration.MigrationService;
import com.sharenote.redistribution.service.migration.MigrationStatisticsService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;
    private final MigrationProgressService progressService;
    private final MigrationStatisticsService statisticsService;

    /**
     * 데이터 재분산 시작
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<MigrationStartResponse>> startMigration() {
        log.info("데이터 재분산 시작 요청 수신");

        try {
            // 비동기로 마이그레이션 실행
            CompletableFuture.runAsync(() -> {
                try {
                    MigrationResult result = migrationService.executeMigration();
                    progressService.completeProgress(result);
                    log.info("데이터 재분산 완료: {}", result.isSuccess() ? "성공" : "실패");
                } catch (Exception e) {
                    log.error("데이터 재분산 실행 중 오류", e);
                    MigrationResult failedResult = MigrationResult.builder()
                            .startTime(LocalDateTime.now())
                            .endTime(LocalDateTime.now())
                            .success(false)
                            .errorMessage(e.getMessage())
                            .build();
                    progressService.completeProgress(failedResult);
                }
            });

            MigrationStartResponse response = MigrationStartResponse.builder()
                    .message("데이터 재분산 작업이 시작되었습니다.")
                    .startTime(LocalDateTime.now())
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("데이터 재분산 시작 중 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("데이터 재분산 시작 실패: " + e.getMessage()));
        }
    }

    /**
     * 마이그레이션 진행 상태 조회
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<MigrationProgressService.MigrationStatus>> getMigrationStatus() {
        try {
            MigrationProgressService.MigrationStatus status = progressService.getCurrentStatus();
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (Exception e) {
            log.error("마이그레이션 상태 조회 중 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("상태 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 마이그레이션 통계 조회
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<MigrationStatisticsService.MigrationStatistics>> getStatistics() {
        try {
            MigrationStatisticsService.MigrationStatistics stats = statisticsService.getStatistics();
            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            log.error("마이그레이션 통계 조회 중 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("통계 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 샤드 분산 현황 조회
     */
    @GetMapping("/shard-distribution")
    public ResponseEntity<ApiResponse<MigrationStatisticsService.ShardDistributionStats>> getShardDistribution() {
        try {
            MigrationStatisticsService.ShardDistributionStats distribution =
                    statisticsService.getShardDistribution();
            return ResponseEntity.ok(ApiResponse.success(distribution));
        } catch (Exception e) {
            log.error("샤드 분산 현황 조회 중 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("분산 현황 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 마이그레이션 시작 응답 클래스
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MigrationStartResponse {
        private String message;
        private LocalDateTime startTime;
    }
}
