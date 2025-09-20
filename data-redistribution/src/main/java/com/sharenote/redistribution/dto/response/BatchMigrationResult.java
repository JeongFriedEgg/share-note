package com.sharenote.redistribution.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class BatchMigrationResult {
    private int successCount = 0;
    private int failureCount = 0;
    private List<UUID> failedPageIds = new ArrayList<>();

    public void incrementSuccess() {
        this.successCount++;
    }

    public void incrementFailure() {
        this.failureCount++;
    }

    public void addFailedPageId(UUID pageId) {
        this.failedPageIds.add(pageId);
    }

    public int getTotalProcessed() {
        return successCount + failureCount;
    }
}