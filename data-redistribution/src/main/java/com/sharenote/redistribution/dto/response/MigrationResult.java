package com.sharenote.redistribution.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MigrationResult {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean success;
    private String errorMessage;
    private long totalPages;
    private long totalProcessed;
    private long totalSuccess;
    private long totalFailures;

    @Builder.Default
    private List<BatchMigrationResult> batchResults = new ArrayList<>();

    public void addBatchResult(BatchMigrationResult batchResult) {
        this.batchResults.add(batchResult);
        this.totalProcessed += batchResult.getTotalProcessed();
        this.totalSuccess += batchResult.getSuccessCount();
        this.totalFailures += batchResult.getFailureCount();
    }

    public Duration getDuration() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime);
        }
        return Duration.ZERO;
    }
}
