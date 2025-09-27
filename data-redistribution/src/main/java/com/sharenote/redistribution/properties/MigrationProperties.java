package com.sharenote.redistribution.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.redistribution")
public class MigrationProperties {
    private int batchSize;
    private int delayBetweenBatches;
    private int retryCount;
}
