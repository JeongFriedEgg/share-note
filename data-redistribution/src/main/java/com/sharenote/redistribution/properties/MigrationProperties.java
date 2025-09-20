package com.sharenote.redistribution.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.redistribution")
public class MigrationProperties {
    private boolean enabled = true;
    private int batchSize = 1000;
    private int threadPoolSize = 10;
    private int retryCount = 3;
    private long delayBetweenBatches = 1000;
    private long progressReportInterval = 10000;

    private Verification verification = new Verification();
    private Cleanup cleanup = new Cleanup();

    @Data
    public static class Verification {
        private boolean enabled = true;
        private double sampleRate = 0.1;
    }

    @Data
    public static class Cleanup {
        private boolean enabled = false;
        private boolean backupBeforeCleanup = true;
    }
}
