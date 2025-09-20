package com.sharenote.redistribution.config;

import com.sharenote.redistribution.properties.RedissonProperties;
import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RedissonConfig {

    private final RedissonProperties redissonProperties;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 단일 서버 설정
        var singleServerConfig = config.useSingleServer()
                .setAddress("redis://" + redissonProperties.getHost() + ":" + redissonProperties.getPort())
                .setTimeout(redissonProperties.getTimeout())
                .setConnectTimeout(redissonProperties.getConnectTimeout())
                .setIdleConnectionTimeout(redissonProperties.getIdleConnectionTimeout())
                .setRetryAttempts(redissonProperties.getRetryAttempts())
                .setRetryInterval(redissonProperties.getRetryInterval());

        // 비밀번호가 설정된 경우에만 비밀번호 설정
        if (redissonProperties.getPassword() != null && !redissonProperties.getPassword().isEmpty()) {
            singleServerConfig.setPassword(redissonProperties.getPassword());
        }

        return Redisson.create(config);
    }

    // Sentinel 설정 (주석 처리)
    // @Bean(destroyMethod = "shutdown")
    // public RedissonClient redissonClient() {
    //     Config config = new Config();
    //
    //     SentinelServersConfig sentinelConfig = config.useSentinelServers()
    //             .setMasterName(redissonProperties.getMasterName())
    //             .setPassword(redissonProperties.getPassword())
    //             .setTimeout(redissonProperties.getTimeout())
    //             .setConnectTimeout(redissonProperties.getConnectTimeout())
    //             .setIdleConnectionTimeout(redissonProperties.getIdleConnectionTimeout())
    //             .setRetryAttempts(redissonProperties.getRetryAttempts())
    //             .setRetryInterval(redissonProperties.getRetryInterval())
    //             .setMasterConnectionMinimumIdleSize(redissonProperties.getMasterConnectionMinimumIdleSize())
    //             .setMasterConnectionPoolSize(redissonProperties.getMasterConnectionPoolSize())
    //             .setSlaveConnectionMinimumIdleSize(redissonProperties.getSlaveConnectionMinimumIdleSize())
    //             .setSlaveConnectionPoolSize(redissonProperties.getSlaveConnectionPoolSize())
    //             .setScanInterval(redissonProperties.getScanInterval());
    //
    //     redissonProperties.getSentinelAddresses().forEach(sentinelConfig::addSentinelAddress);
    //
    //     return Redisson.create(config);
    // }
}
