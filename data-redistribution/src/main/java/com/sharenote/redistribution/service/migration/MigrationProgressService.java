package com.sharenote.redistribution.service.migration;

import com.sharenote.redistribution.dto.response.MigrationResult;
import com.sharenote.redistribution.util.JsonUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationProgressService {

    private final StringRedisTemplate redisTemplate;

    private static final String PROGRESS_KEY = "migration:progress";
    private static final String STATUS_KEY = "migration:status";
    private static final String STATS_KEY = "migration:stats";

    @Value("${app.redistribution.progress-report-interval:10000}")
    private long progressReportInterval;

    private final AtomicLong processedCount = new AtomicLong(0);
    private long totalCount = 0;
    private LocalDateTime startTime;

    /**
     * 진행률 추적 초기화
     */
    public void initializeProgress(long totalPages) {
        this.totalCount = totalPages;
        this.startTime = LocalDateTime.now();
        this.processedCount.set(0);

        // Redis에 초기 상태 저장
        MigrationStatus status = MigrationStatus.builder()
                .totalPages(totalPages)
                .processedPages(0)
                .startTime(startTime)
                .status("RUNNING")
                .build();

        saveStatusToRedis(status);
        log.info("마이그레이션 진행률 추적 초기화 - 총 {}개 페이지", totalPages);
    }

    /**
     * 처리된 페이지 수 증가 및 진행률 업데이트
     */
    public void incrementProgress() {
        long currentProcessed = processedCount.incrementAndGet();

        // 주기적으로 진행률 보고
        if (currentProcessed % progressReportInterval == 0 || currentProcessed == totalCount) {
            double progressPercentage = (double) currentProcessed / totalCount * 100;
            Duration elapsed = Duration.between(startTime, LocalDateTime.now());

            // 예상 완료 시간 계산
            Duration estimatedTotal = Duration.ZERO;
            LocalDateTime estimatedEndTime = null;

            if (currentProcessed > 0) {
                long totalMillis = elapsed.toMillis() * totalCount / currentProcessed;
                estimatedTotal = Duration.ofMillis(totalMillis);
                estimatedEndTime = startTime.plus(estimatedTotal);
            }

            MigrationStatus status = MigrationStatus.builder()
                    .totalPages(totalCount)
                    .processedPages(currentProcessed)
                    .progressPercentage(progressPercentage)
                    .startTime(startTime)
                    .elapsedTime(elapsed)
                    .estimatedEndTime(estimatedEndTime)
                    .estimatedTotalTime(estimatedTotal)
                    .status("RUNNING")
                    .build();

            saveStatusToRedis(status);

            log.info("마이그레이션 진행률: {}/{} ({:.2f}%) - 경과시간: {}, 예상완료: {}",
                    currentProcessed, totalCount, progressPercentage,
                    formatDuration(elapsed),
                    estimatedEndTime != null ? estimatedEndTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "계산중");
        }
    }

    /**
     * 마이그레이션 완료 처리
     */
    public void completeProgress(MigrationResult result) {
        Duration totalElapsed = result.getDuration();

        MigrationStatus status = MigrationStatus.builder()
                .totalPages(result.getTotalPages())
                .processedPages(result.getTotalProcessed())
                .successPages(result.getTotalSuccess())
                .failedPages(result.getTotalFailures())
                .progressPercentage(100.0)
                .startTime(result.getStartTime())
                .endTime(result.getEndTime())
                .elapsedTime(totalElapsed)
                .status(result.isSuccess() ? "COMPLETED" : "FAILED")
                .errorMessage(result.getErrorMessage())
                .build();

        saveStatusToRedis(status);

        log.info("마이그레이션 완료 - 총 처리: {}개, 성공: {}개, 실패: {}개, 소요시간: {}",
                result.getTotalProcessed(), result.getTotalSuccess(),
                result.getTotalFailures(), formatDuration(totalElapsed));
    }

    /**
     * 현재 진행 상태 조회
     */
    public MigrationStatus getCurrentStatus() {
        try {
            String statusJson = redisTemplate.opsForValue().get(STATUS_KEY);
            if (statusJson != null) {
                return JsonUtils.fromJson(statusJson, MigrationStatus.class);
            }
        } catch (Exception e) {
            log.error("마이그레이션 상태 조회 중 오류", e);
        }

        return MigrationStatus.builder()
                .status("UNKNOWN")
                .errorMessage("상태 정보를 조회할 수 없습니다.")
                .build();
    }

    /**
     * Redis에 상태 저장
     */
    private void saveStatusToRedis(MigrationStatus status) {
        try {
            String statusJson = JsonUtils.toJson(status);
            redisTemplate.opsForValue().set(STATUS_KEY, statusJson, Duration.ofHours(24));
        } catch (Exception e) {
            log.error("마이그레이션 상태 저장 중 오류", e);
        }
    }

    /**
     * Duration을 읽기 쉬운 형태로 포맷팅
     */
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * 마이그레이션 통계 정보 클래스
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MigrationStatus {
        private long totalPages;
        private long processedPages;
        private long successPages;
        private long failedPages;
        private double progressPercentage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private LocalDateTime estimatedEndTime;
        private Duration elapsedTime;
        private Duration estimatedTotalTime;
        private String status;
        private String errorMessage;
    }
}
