package com.sharenote.redistribution.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.database.sharding")
public class ShardingProperties {
    private boolean enabled = true;
    private String strategy = "hash";
    private int shardCount = 2;
    private String defaultShard = "legacy";
}
