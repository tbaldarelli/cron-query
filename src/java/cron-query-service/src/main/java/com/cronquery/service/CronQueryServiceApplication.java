package com.cronquery.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for the Cron Query Service.
 * 
 * This microservice wraps the existing Groovy implementation of cron-query
 * with REST endpoints, enabling HTTP-based access to cron schedule analysis.
 */
@SpringBootApplication
public class CronQueryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CronQueryServiceApplication.class, args);
    }

}
