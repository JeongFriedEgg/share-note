package com.sharenote.redistribution.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.datasource")
public class DatabaseProperties {

    private DataSourceConfig legacy = new DataSourceConfig();
    private DataSourceConfig shard1 = new DataSourceConfig();
    private DataSourceConfig shard2 = new DataSourceConfig();

    @Data
    public static class DataSourceConfig {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "org.postgresql.Driver";
        private HikariConfig hikari = new HikariConfig();

        @Data
        public static class HikariConfig {
            private int maximumPoolSize = 20;
            private int minimumIdle = 5;
            private long connectionTimeout = 30000;
            private long idleTimeout = 300000;
            private long leakDetectionThreshold = 60000;
            private String poolName;
        }
    }
}