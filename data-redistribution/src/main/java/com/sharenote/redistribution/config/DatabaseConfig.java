package com.sharenote.redistribution.config;

import com.sharenote.redistribution.properties.DatabaseProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseConfig {

    private final DatabaseProperties databaseProperties;

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
        config.setPoolName(poolName);
        config.setMaximumPoolSize(hikariConfig.getMaximumPoolSize());
        config.setMinimumIdle(hikariConfig.getMinimumIdle());
        config.setIdleTimeout(hikariConfig.getIdleTimeout());
        config.setConnectionTimeout(hikariConfig.getConnectionTimeout());
        config.setLeakDetectionThreshold(hikariConfig.getLeakDetectionThreshold());

        return config;
    }

    /**
     * JPA 속성 설정
     */
    private Map<String, Object> createJpaProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.ddl-auto", "none");
        properties.put("hibernate.show_sql", false);
        properties.put("hibernate.format_sql", true);
        properties.put("hibernate.use_sql_comments", false);
        properties.put("hibernate.jdbc.batch_size", 1000);
        properties.put("hibernate.jdbc.batch_versioned_data", true);
        properties.put("hibernate.order_inserts", true);
        properties.put("hibernate.order_updates", true);

        return properties;
    }

    // ================================
    // Legacy DataSource 설정
    // ================================

    /**
     * 레거시 데이터소스 설정 (기본)
     */
    @Bean("legacyDataSource")
    @Primary
    public DataSource legacyDataSource() {
        DatabaseProperties.DataSourceConfig legacyConfig = databaseProperties.getLegacy();
        HikariConfig config = createHikariConfig(legacyConfig,"LegacyHikariPool");
        log.info("Legacy DataSource 초기화: {}", legacyConfig.getUrl());
        return new HikariDataSource(config);
    }

    /**
     * Legacy EntityManagerFactory
     */
    @Bean("legacyEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean legacyEntityManagerFactory(
            @Qualifier("legacyDataSource") DataSource legacyDataSource) {

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setShowSql(false);
        vendorAdapter.setDatabase(Database.POSTGRESQL);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(legacyDataSource);
        factory.setPackagesToScan("com.sharenote.redistribution.entity");
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(createJpaProperties());
        factory.setPersistenceUnitName("legacy");

        return factory;
    }

    /**
     * Legacy TransactionManager
     */
    @Bean("legacyTransactionManager")
    public PlatformTransactionManager legacyTransactionManager(
            @Qualifier("legacyEntityManagerFactory") EntityManagerFactory legacyEntityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(legacyEntityManagerFactory);
        return transactionManager;
    }


    // ================================
    // Shard1 DataSource 설정
    // ================================

    /**
     * 샤드1 데이터소스 설정
     */
    @Bean("shard1DataSource")
    public DataSource shard1DataSource() {
        DatabaseProperties.DataSourceConfig shard1Config = databaseProperties.getShard1();
        HikariConfig config = createHikariConfig(shard1Config,"Shard1HikariPool");
        log.info("Shard1 DataSource 초기화: {}", shard1Config.getUrl());
        return new HikariDataSource(config);
    }

    /**
     * Shard1 EntityManagerFactory
     */
    @Bean("shard1EntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean shard1EntityManagerFactory(
            @Qualifier("shard1DataSource") DataSource shard1DataSource) {

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setShowSql(false);
        vendorAdapter.setDatabase(Database.POSTGRESQL);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(shard1DataSource);
        factory.setPackagesToScan("com.sharenote.redistribution.entity");
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(createJpaProperties());
        factory.setPersistenceUnitName("shard1");

        return factory;
    }

    /**
     * Shard1 TransactionManager
     */
    @Bean("shard1TransactionManager")
    public PlatformTransactionManager shard1TransactionManager(
            @Qualifier("shard1EntityManagerFactory") EntityManagerFactory shard1EntityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(shard1EntityManagerFactory);
        return transactionManager;
    }

    // ================================
    // Shard2 DataSource 설정
    // ================================

    /**
     * 샤드2 데이터소스 설정
     */
    @Bean("shard2DataSource")
    public DataSource shard2DataSource() {
        DatabaseProperties.DataSourceConfig shard2Config = databaseProperties.getShard2();
        HikariConfig config = createHikariConfig(shard2Config,"Shard2HikariPool");
        log.info("Shard2 DataSource 초기화: {}", shard2Config.getUrl());
        return new HikariDataSource(config);
    }

    /**
     * Shard2 EntityManagerFactory
     */
    @Bean("shard2EntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean shard2EntityManagerFactory(
            @Qualifier("shard2DataSource") DataSource shard2DataSource) {

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setShowSql(false);
        vendorAdapter.setDatabase(Database.POSTGRESQL);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(shard2DataSource);
        factory.setPackagesToScan("com.sharenote.redistribution.entity");
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(createJpaProperties());
        factory.setPersistenceUnitName("shard2");

        return factory;
    }

    /**
     * Shard2 TransactionManager
     */
    @Bean("shard2TransactionManager")
    public PlatformTransactionManager shard2TransactionManager(
            @Qualifier("shard2EntityManagerFactory") EntityManagerFactory shard2EntityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(shard2EntityManagerFactory);
        return transactionManager;
    }

    // ================================
    // Primary 설정 (기본값으로 Legacy 사용)
    // ================================


    /**
     * Primary EntityManagerFactory (기본값: Legacy)
     */
    @Bean("entityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            @Qualifier("legacyDataSource") DataSource legacyDataSource) {
        return legacyEntityManagerFactory(legacyDataSource);
    }

    /**
     * Primary TransactionManager (기본값: Legacy)
     */
    @Bean("transactionManager")
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("legacyEntityManagerFactory") EntityManagerFactory legacyEntityManagerFactory) {
        return legacyTransactionManager(legacyEntityManagerFactory);
    }

}
