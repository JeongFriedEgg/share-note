package com.sharenote.redistribution.properties;

import com.sharenote.redistribution.exception.custom.DistributedLockException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "redisson")
public class RedissonProperties {
    private ServerConfig server = new ServerConfig();
    private PoolConfig pool = new PoolConfig();
    private LockConfig lock = new LockConfig();
    /**
     * 레디스 서버 설정
     */
    @Data
    public static class ServerConfig {
        private String host;
        private int port;
        private String password;
        private int database;
        private int timeout;
        private int connectTimeout;

        public String getAddress() {
            return String.format("redis://%s:%d", host, port);
        }

        public boolean hasPassword() {
            return password != null && !password.trim().isEmpty();
        }
    }

    /**
     * 연결 풀 설정
     */
    @Data
    public static class PoolConfig {
        private int connectionPoolSize;
        private int connectionMinimumIdleSize;
        private int idleConnectionTimeout;
        private int retryAttempts;
        private int retryInterval;
    }

    /**
     * 분산락 설정
     */
    @Data
    public static class LockConfig {
        private long watchdogTimeout;
        private long defaultWaitTime;
        private long defaultLeaseTime;
        private String keyPrefix;

        /**
         * 완전한 락 키 생성
         */
        public String buildLockKey(String key) {
            if (key == null || key.trim().isEmpty()) {
                throw new DistributedLockException("락 키는 비어있을 수 없습니다.");
            }
            return keyPrefix + key.trim();
        }
    }
}
