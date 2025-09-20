package com.sharenote.redistribution.service.migration;

import com.sharenote.redistribution.enums.MigrationStatus;
import com.sharenote.redistribution.repository.BlockRepository;
import com.sharenote.redistribution.repository.PagePermissionRepository;
import com.sharenote.redistribution.repository.PageRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationStatisticsService {

    private final PageRepository pageRepository;

    /**
     * 마이그레이션 통계 조회
     */
    public MigrationStatistics getStatistics() {
        try {
            // 페이지별 마이그레이션 상태 통계
            List<Object[]> pageStats = pageRepository.countByMigrationStatus();
            Map<MigrationStatus, Long> pageStatusCounts = pageStats.stream()
                    .collect(Collectors.toMap(
                            row -> (MigrationStatus) row[0],
                            row -> (Long) row[1]
                    ));

            // 전체 통계 계산
            long totalPages = pageStatusCounts.values().stream().mapToLong(Long::longValue).sum();
            long readyPages = pageStatusCounts.getOrDefault(MigrationStatus.READY, 0L);
            long migratingPages = pageStatusCounts.getOrDefault(MigrationStatus.MIGRATING, 0L);
            long migratedPages = pageStatusCounts.getOrDefault(MigrationStatus.MIGRATED, 0L);

            double progressPercentage = totalPages > 0 ?
                    (double) migratedPages / totalPages * 100 : 0.0;

            return MigrationStatistics.builder()
                    .totalPages(totalPages)
                    .readyPages(readyPages)
                    .migratingPages(migratingPages)
                    .migratedPages(migratedPages)
                    .progressPercentage(progressPercentage)
                    .lastUpdated(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("마이그레이션 통계 조회 중 오류", e);
            throw new RuntimeException("마이그레이션 통계를 조회할 수 없습니다.", e);
        }
    }

    /**
     * 샤드별 데이터 분산 현황 조회
     */
    public ShardDistributionStats getShardDistribution() {
        // TODO: 각 샤드별로 데이터 개수 조회 구현
        return ShardDistributionStats.builder()
                .legacyCount(0L)
                .shard1Count(0L)
                .shard2Count(0L)
                .build();
    }

    /**
     * 마이그레이션 통계 클래스
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MigrationStatistics {
        private long totalPages;
        private long readyPages;
        private long migratingPages;
        private long migratedPages;
        private double progressPercentage;
        private LocalDateTime lastUpdated;
    }

    /**
     * 샤드 분산 통계 클래스
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ShardDistributionStats {
        private long legacyCount;
        private long shard1Count;
        private long shard2Count;
    }
}
