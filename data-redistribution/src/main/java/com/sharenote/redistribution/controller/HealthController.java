package com.sharenote.redistribution.controller;

import com.sharenote.redistribution.dto.response.ApiResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource legacyDataSource;
    private final DataSource shard1DataSource;
    private final DataSource shard2DataSource;
    private final RedissonClient redissonClient;

    /**
     * 전체 헬스체크
     */
    @GetMapping
    public ResponseEntity<ApiResponse<HealthStatus>> checkHealth() {
        HealthStatus.HealthStatusBuilder builder = HealthStatus.builder();
        boolean allHealthy = true;

        // 데이터베이스 연결 상태 확인
        DatabaseHealthStatus dbHealth = checkDatabaseHealth();
        builder.databaseStatus(dbHealth);
        if (!dbHealth.isAllHealthy()) {
            allHealthy = false;
        }

        // Redis 연결 상태 확인
        RedisHealthStatus redisHealth = checkRedisHealth();
        builder.redisStatus(redisHealth);
        if (!redisHealth.isHealthy()) {
            allHealthy = false;
        }

        HealthStatus status = builder
                .status(allHealthy ? "UP" : "DOWN")
                .timestamp(LocalDateTime.now())
                .build();

        HttpStatus httpStatus = allHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(ApiResponse.success(status));
    }

    /**
     * 데이터베이스 헬스체크
     */
    @GetMapping("/database")
    public ResponseEntity<ApiResponse<DatabaseHealthStatus>> checkDatabaseHealthProcess() {
        DatabaseHealthStatus status = checkDatabaseHealth();
        HttpStatus httpStatus = status.isAllHealthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(ApiResponse.success(status));
    }

    /**
     * Redis 헬스체크
     */
    @GetMapping("/redis")
    public ResponseEntity<ApiResponse<RedisHealthStatus>> checkRedisHealthProcess() {
        RedisHealthStatus status = checkRedisHealth();
        HttpStatus httpStatus = status.isHealthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(ApiResponse.success(status));
    }

    /**
     * 데이터베이스 연결 상태 확인 구현
     */
    private DatabaseHealthStatus checkDatabaseHealth() {
        DatabaseHealthStatus.DatabaseHealthStatusBuilder builder = DatabaseHealthStatus.builder();

        // Legacy 데이터베이스 확인
        boolean legacyHealthy = testDatabaseConnection(legacyDataSource, "legacy");
        builder.legacyStatus(legacyHealthy ? "UP" : "DOWN");

        // Shard1 데이터베이스 확인
        boolean shard1Healthy = testDatabaseConnection(shard1DataSource, "shard1");
        builder.shard1Status(shard1Healthy ? "UP" : "DOWN");

        // Shard2 데이터베이스 확인
        boolean shard2Healthy = testDatabaseConnection(shard2DataSource, "shard2");
        builder.shard2Status(shard2Healthy ? "UP" : "DOWN");

        return builder.build();
    }

    /**
     * Redis 연결 상태 확인 구현
     */
    private RedisHealthStatus checkRedisHealth() {
        try {
            // Redis ping 테스트
            redissonClient.getKeys().count();
            return RedisHealthStatus.builder()
                    .status("UP")
                    .message("Redis 연결 정상")
                    .build();
        } catch (Exception e) {
            log.error("Redis 헬스체크 실패", e);
            return RedisHealthStatus.builder()
                    .status("DOWN")
                    .message("Redis 연결 실패: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 데이터베이스 연결 테스트
     */
    private boolean testDatabaseConnection(DataSource dataSource, String shardName) {
        try (Connection connection = dataSource.getConnection()) {
            // 간단한 쿼리 실행으로 연결 테스트
            try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
                stmt.executeQuery();
                log.debug("데이터베이스 {} 연결 정상", shardName);
                return true;
            }
        } catch (Exception e) {
            log.error("데이터베이스 {} 연결 실패", shardName, e);
            return false;
        }
    }

    /**
     * 전체 헬스 상태 클래스
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HealthStatus {
        private String status;
        private LocalDateTime timestamp;
        private DatabaseHealthStatus databaseStatus;
        private RedisHealthStatus redisStatus;
    }

    /**
     * 데이터베이스 헬스 상태 클래스
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DatabaseHealthStatus {
        private String legacyStatus;
        private String shard1Status;
        private String shard2Status;

        public boolean isAllHealthy() {
            return "UP".equals(legacyStatus) &&
                    "UP".equals(shard1Status) &&
                    "UP".equals(shard2Status);
        }
    }

    /**
     * Redis 헬스 상태 클래스
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RedisHealthStatus {
        private String status;
        private String message;

        public boolean isHealthy() {
            return "UP".equals(status);
        }
    }
}
