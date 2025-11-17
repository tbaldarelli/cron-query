package com.cronquery.service.integration;

import com.cronquery.service.model.CronJob;
import com.cronquery.service.model.QueryRequest;

import java.util.List;

/**
 * Adapter interface for integrating with the Groovy JAR implementation of cron-query.
 * 
 * This interface provides a clean abstraction layer between the Spring Boot service
 * and the Groovy JAR, allowing for easier testing and potential alternative implementations.
 */
public interface GroovyJarAdapter {
    
    /**
     * Query cron jobs based on the provided request criteria.
     * 
     * @param request The query request containing natural language query or structured parameters
     * @param crontabContent The raw crontab content to analyze
     * @return List of matching cron jobs
     * @throws GroovyJarException If the Groovy JAR invocation fails
     */
    List<CronJob> queryJobs(QueryRequest request, String crontabContent);
    
    /**
     * Load and parse all cron jobs from the provided crontab content.
     * 
     * @param crontabContent The raw crontab content to parse
     * @return List of all parsed cron jobs
     * @throws GroovyJarException If parsing fails
     */
    List<CronJob> loadAllJobs(String crontabContent);
    
    /**
     * Validate a cron expression using the Groovy JAR's validation logic.
     * 
     * @param cronExpression The cron expression to validate
     * @return true if valid, false otherwise
     */
    boolean validateCronExpression(String cronExpression);
}
