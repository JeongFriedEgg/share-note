package com.sharenote.redistribution.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.redisson")
public class RedissonProperties {
    // 단일 서버 설정
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int timeout = 10000;
    private int connectTimeout = 10000;
    private int idleConnectionTimeout = 30000;
    private int retryAttempts = 3;
    private int retryInterval = 2000;

    // Sentinel 설정 (주석 처리)
    // private String masterName;
    // private List<String> sentinelAddresses;
    // private int masterConnectionMinimumIdleSize = 10;
    // private int masterConnectionPoolSize = 20;
    // private int slaveConnectionMinimumIdleSize = 10;
    // private int slaveConnectionPoolSize = 20;
    // private int scanInterval = 2000;
}
