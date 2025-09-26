package com.sharenote.redistribution.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationProgressInfo {
    private long totalPages;
    private long readyPages;
    private long migratingPages;
    private long migratedPages;
    private long failedPages;

    /**
     * 마이그레이션 진행률 (퍼센트)
     * @return
     */
    public double getProgressPercentage() {
        if (totalPages == 0) {
            return 0.0;
        }
        return (double) migratedPages / totalPages * 100;
    }

    /**
     * 마이그레이션이 완료되었는지 확인하는 메서드
     * @return
     */
    public boolean isCompleted() {
        return readyPages == 0 && migratingPages == 0 && failedPages == 0;
    }

    /**
     * 50% 이상 마이그레이션되었는지 확인하는 메서드
     * @return
     */
    public boolean isMoreThanHalfCompleted() {
        return getProgressPercentage() >= 50.0;
    }
}
