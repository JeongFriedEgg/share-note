package com.sharenote.redistribution.config;

import com.sharenote.redistribution.properties.DatabaseProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableJpaRepositories(
    basePackages = "com.sharenote.redistribution.repository",
    entityManagerFactoryRef = "legacyEntityManagerFactory",
    transactionManagerRef = "legacyTransactionManager"
)
public class DatabaseConfig {

    private final DatabaseProperties databaseProperties;

    /**
     * 레거시 데이터소스 설정 (기본)
     */
    @Bean
    @Primary
    public DataSource legacyDataSource() {
        DatabaseProperties.DataSourceConfig legacyConfig = databaseProperties.getLegacy();
        HikariConfig config = createHikariConfig(legacyConfig, "LegacyHikariPool");
        log.info("Legacy DataSource 초기화: {}", legacyConfig.getUrl());
        return new HikariDataSource(config);
    }

    /**
     * 샤드1 데이터소스 설정
     */
    @Bean
    public DataSource shard1DataSource() {
        DatabaseProperties.DataSourceConfig shard1Config = databaseProperties.getShard1();
        HikariConfig config = createHikariConfig(shard1Config, "Shard1HikariPool");
        log.info("Shard1 DataSource 초기화: {}", shard1Config.getUrl());
        return new HikariDataSource(config);
    }

    /**
     * 샤드2 데이터소스 설정
     */
    @Bean
    public DataSource shard2DataSource() {
        DatabaseProperties.DataSourceConfig shard2Config = databaseProperties.getShard2();
        HikariConfig config = createHikariConfig(shard2Config, "Shard2HikariPool");
        log.info("Shard2 DataSource 초기화: {}", shard2Config.getUrl());
        return new HikariDataSource(config);
    }

    /**
     * HikariConfig 생성 유틸리티 메서드
     */
    private HikariConfig createHikariConfig(DatabaseProperties.DataSourceConfig dataSourceConfig, String poolName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dataSourceConfig.getUrl());
        config.setUsername(dataSourceConfig.getUsername());
        config.setPassword(dataSourceConfig.getPassword());
        config.setDriverClassName(dataSourceConfig.getDriverClassName());

        DatabaseProperties.DataSourceConfig.HikariConfig hikariConfig = dataSourceConfig.getHikari();
        config.setMaximumPoolSize(hikariConfig.getMaximumPoolSize());
        config.setMinimumIdle(hikariConfig.getMinimumIdle());
        config.setConnectionTimeout(hikariConfig.getConnectionTimeout());
        config.setIdleTimeout(hikariConfig.getIdleTimeout());
        config.setLeakDetectionThreshold(hikariConfig.getLeakDetectionThreshold());
        config.setPoolName(poolName);

        return config;
    }

    /**
     * 레거시 엔티티 매니저 팩토리 (기본)
     */
    @Bean("legacyEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean legacyEntityManagerFactory(
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(legacyDataSource())
                .packages("com.sharenote.redistribution.entity")
                .persistenceUnit("legacy")
                .build();
    }

    /**
     * 샤드1 엔티티 매니저 팩토리
     */
    @Bean("shard1EntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean shard1EntityManagerFactory(
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(shard1DataSource())
                .packages("com.sharenote.redistribution.entity")
                .persistenceUnit("shard1")
                .build();
    }

    /**
     * 샤드2 엔티티 매니저 팩토리
     */
    @Bean("shard2EntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean shard2EntityManagerFactory(
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(shard2DataSource())
                .packages("com.sharenote.redistribution.entity")
                .persistenceUnit("shard2")
                .build();
    }

    /**
     * 레거시 트랜잭션 매니저 (기본)
     */
    @Bean("legacyTransactionManager")
    @Primary
    public PlatformTransactionManager legacyTransactionManager(
            @Qualifier("legacyEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /**
     * 샤드1 트랜잭션 매니저
     */
    @Bean("shard1TransactionManager")
    public PlatformTransactionManager shard1TransactionManager(
            @Qualifier("shard1EntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /**
     * 샤드2 트랜잭션 매니저
     */
    @Bean("shard2TransactionManager")
    public PlatformTransactionManager shard2TransactionManager(
            @Qualifier("shard2EntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
