package com.cronquery.service.health;

import com.cronquery.service.integration.GroovyJarAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for the Groovy JAR integration.
 * Verifies that the Groovy JAR can be invoked successfully.
 */
@Component
public class GroovyJarHealthIndicator implements HealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(GroovyJarHealthIndicator.class);
    
    private final GroovyJarAdapter groovyJarAdapter;
    
    @Value("${cronquery.groovy-jar.version}")
    private String groovyJarVersion;
    
    public GroovyJarHealthIndicator(GroovyJarAdapter groovyJarAdapter) {
        this.groovyJarAdapter = groovyJarAdapter;
    }
    
    @Override
    public Health health() {
        try {
            // Test basic functionality by validating a simple cron expression
            boolean isValid = groovyJarAdapter.validateCronExpression("0 0 * * *");
            
            if (isValid) {
                return Health.up()
                    .withDetail("status", "Groovy JAR integration operational")
                    .withDetail("version", groovyJarVersion)
                    .withDetail("validationTest", "passed")
                    .build();
            } else {
                return Health.down()
                    .withDetail("status", "Groovy JAR validation test failed")
                    .withDetail("version", groovyJarVersion)
                    .withDetail("validationTest", "failed")
                    .build();
            }
            
        } catch (Exception e) {
            logger.error("Health check failed for Groovy JAR integration", e);
            return Health.down()
                .withDetail("status", "Groovy JAR integration error")
                .withDetail("version", groovyJarVersion)
                .withDetail("error", e.getMessage())
                .withException(e)
                .build();
        }
    }
}
