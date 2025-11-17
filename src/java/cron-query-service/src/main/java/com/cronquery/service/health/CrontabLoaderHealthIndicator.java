package com.cronquery.service.health;

import com.cronquery.service.integration.CrontabLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Custom health indicator for the CrontabLoader component.
 * Checks if crontab sources are accessible and provides details about loaded sources.
 */
@Component
public class CrontabLoaderHealthIndicator implements HealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(CrontabLoaderHealthIndicator.class);
    
    private final CrontabLoader crontabLoader;
    
    public CrontabLoaderHealthIndicator(CrontabLoader crontabLoader) {
        this.crontabLoader = crontabLoader;
    }
    
    @Override
    public Health health() {
        try {
            // Attempt to load crontab data
            String crontabData = crontabLoader.loadCrontabData();
            List<String> activeSources = crontabLoader.getActiveSources();
            int jobCount = crontabLoader.getJobCount();
            
            if (activeSources.isEmpty() || jobCount == 0) {
                return Health.down()
                    .withDetail("status", "No crontab sources available")
                    .withDetail("activeSources", activeSources)
                    .withDetail("jobCount", jobCount)
                    .build();
            }
            
            return Health.up()
                .withDetail("status", "Crontab sources accessible")
                .withDetail("activeSources", activeSources)
                .withDetail("jobCount", jobCount)
                .withDetail("sourceCount", activeSources.size())
                .build();
                
        } catch (Exception e) {
            logger.error("Health check failed for CrontabLoader", e);
            return Health.down()
                .withDetail("status", "Failed to load crontab data")
                .withDetail("error", e.getMessage())
                .withException(e)
                .build();
        }
    }
}
