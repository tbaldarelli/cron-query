package com.cronquery.service.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for custom application metrics.
 * Defines counters and timers for tracking application behavior.
 */
@Configuration
public class MetricsConfig {
    
    /**
     * Counter for total API requests.
     */
    @Bean
    public Counter apiRequestCounter(MeterRegistry registry) {
        return Counter.builder("cronquery.requests.total")
            .description("Total number of API requests")
            .tag("application", "cron-query-service")
            .register(registry);
    }
    
    /**
     * Timer for API request duration.
     */
    @Bean
    public Timer apiRequestTimer(MeterRegistry registry) {
        return Timer.builder("cronquery.requests.duration")
            .description("Duration of API requests")
            .tag("application", "cron-query-service")
            .register(registry);
    }
    
    /**
     * Counter for Groovy JAR invocations.
     */
    @Bean
    public Counter groovyJarInvocationCounter(MeterRegistry registry) {
        return Counter.builder("cronquery.groovyjar.invocations")
            .description("Number of Groovy JAR invocations")
            .tag("application", "cron-query-service")
            .register(registry);
    }
    
    /**
     * Timer for Groovy JAR execution duration.
     */
    @Bean
    public Timer groovyJarExecutionTimer(MeterRegistry registry) {
        return Timer.builder("cronquery.groovyjar.duration")
            .description("Duration of Groovy JAR executions")
            .tag("application", "cron-query-service")
            .register(registry);
    }
    
    /**
     * Counter for errors by type.
     */
    @Bean
    public Counter errorCounter(MeterRegistry registry) {
        return Counter.builder("cronquery.errors.total")
            .description("Total number of errors")
            .tag("application", "cron-query-service")
            .register(registry);
    }
}
