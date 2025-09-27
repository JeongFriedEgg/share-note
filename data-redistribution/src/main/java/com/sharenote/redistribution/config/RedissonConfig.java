package com.sharenote.redistribution.config;

import com.sharenote.redistribution.properties.RedissonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RedissonProperties.class)
public class RedissonConfig {
    private final RedissonProperties redissonProperties;
    private RedissonClient redissonClient;

    /**
     * RedissonClient 빈 생성
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 단일 서버 설정
        SingleServerConfig singleServerConfig = config.useSingleServer();

        // 서버 연결 설정
        RedissonProperties.ServerConfig serverConfig = redissonProperties.getServer();
        singleServerConfig.setAddress(serverConfig.getAddress());
        singleServerConfig.setDatabase(serverConfig.getDatabase());
        singleServerConfig.setTimeout(serverConfig.getTimeout());
        singleServerConfig.setConnectTimeout(serverConfig.getConnectTimeout());

        // 패스워드 설정 (있는 경우에만)
        if (serverConfig.hasPassword()) {
            singleServerConfig.setPassword(serverConfig.getPassword());
        }

        // 연결풀 설정
        RedissonProperties.PoolConfig poolConfig = redissonProperties.getPool();
        singleServerConfig.setConnectionPoolSize(poolConfig.getConnectionPoolSize());
        singleServerConfig.setConnectionMinimumIdleSize(poolConfig.getConnectionMinimumIdleSize());
        singleServerConfig.setIdleConnectionTimeout(poolConfig.getIdleConnectionTimeout());
        singleServerConfig.setRetryAttempts(poolConfig.getRetryAttempts());
        singleServerConfig.setRetryInterval(poolConfig.getRetryInterval());

        // Watchdog 설정 (자동 락 갱신)
        config.setLockWatchdogTimeout(redissonProperties.getLock().getWatchdogTimeout());

        // 스레드 풀 설정
        config.setThreads(Runtime.getRuntime().availableProcessors() * 2);
        config.setNettyThreads(Runtime.getRuntime().availableProcessors() * 2);

        redissonClient = Redisson.create(config);

        return redissonClient;
    }
}
