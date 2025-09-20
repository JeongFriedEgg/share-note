package com.sharenote.redistribution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration.class
})
@EnableTransactionManagement
@EnableCaching
@EnableAsync
@EnableScheduling
public class DataRedistributionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataRedistributionApplication.class, args);
    }
}