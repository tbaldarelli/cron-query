package com.cronquery.service;

import com.cronquery.service.exception.CrontabLoadException;
import com.cronquery.service.exception.GroovyJarException;
import com.cronquery.service.exception.InvalidQueryException;
import com.cronquery.service.integration.CrontabLoader;
import com.cronquery.service.integration.GroovyJarAdapter;
import com.cronquery.service.model.CronJob;
import com.cronquery.service.model.HealthStatus;
import com.cronquery.service.model.QueryRequest;
import com.cronquery.service.model.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of CronQueryService that orchestrates crontab loading and query execution.
 * 
 * This service coordinates between the CrontabLoader and GroovyJarAdapter to:
 * 1. Load crontab data from configured sources
 * 2. Execute queries using the Groovy JAR implementation
 * 3. Transform results into API response format
 * 4. Track execution time and provide health status
 */
@Service
public class CronQueryServiceImpl implements CronQueryService {
    
    private static final Logger logger = LoggerFactory.getLogger(CronQueryServiceImpl.class);
    
    private final CrontabLoader crontabLoader;
    private final GroovyJarAdapter groovyJarAdapter;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param crontabLoader The crontab loader for accessing cron data
     * @param groovyJarAdapter The Groovy JAR adapter for query execution
     */
    public CronQueryServiceImpl(CrontabLoader crontabLoader, GroovyJarAdapter groovyJarAdapter) {
        this.crontabLoader = crontabLoader;
        this.groovyJarAdapter = groovyJarAdapter;
    }
    
    @Override
    public QueryResponse executeQuery(QueryRequest request) {
        logger.info("Executing query: {}", request);
        
        // Validate request
        validateRequest(request);
        
        // Track execution time
        long startTime = System.currentTimeMillis();
        
        try {
            // Load crontab data
            String crontabContent = crontabLoader.loadCrontabData();
            logger.debug("Loaded crontab data from {} sources", crontabLoader.getActiveSources().size());
            
            // Execute query using Groovy JAR
            List<CronJob> matchingJobs = groovyJarAdapter.queryJobs(request, crontabContent);
            logger.debug("Query returned {} matching jobs", matchingJobs.size());
            
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Build response
            QueryResponse response = new QueryResponse();
            response.setJobs(matchingJobs);
            response.setTotalCount(matchingJobs.size());
            response.setQuery(buildQueryString(request));
            response.setSources(crontabLoader.getActiveSources());
            response.setExecutionTimeMs(executionTime);
            
            logger.info("Query completed in {}ms, found {} jobs", executionTime, matchingJobs.size());
            
            return response;
            
        } catch (CrontabLoadException e) {
            logger.error("Failed to load crontab data", e);
            throw e;
        } catch (GroovyJarException e) {
            logger.error("Failed to execute query with Groovy JAR", e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during query execution", e);
            throw new GroovyJarException("Unexpected error during query execution: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<CronJob> getAllJobs() {
        logger.info("Loading all cron jobs");
        
        try {
            // Load crontab data
            String crontabContent = crontabLoader.loadCrontabData();
            
            // Load all jobs using Groovy JAR
            List<CronJob> allJobs = groovyJarAdapter.loadAllJobs(crontabContent);
            logger.info("Loaded {} total jobs from {} sources", 
                       allJobs.size(), crontabLoader.getActiveSources().size());
            
            return allJobs;
            
        } catch (CrontabLoadException e) {
            logger.error("Failed to load crontab data", e);
            throw e;
        } catch (GroovyJarException e) {
            logger.error("Failed to load jobs with Groovy JAR", e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while loading all jobs", e);
            throw new GroovyJarException("Unexpected error while loading jobs: " + e.getMessage(), e);
        }
    }
    
    @Override
    public HealthStatus checkHealth() {
        logger.debug("Checking service health");
        
        HealthStatus healthStatus = new HealthStatus();
        
        try {
            // Check crontab loader
            String crontabContent = crontabLoader.loadCrontabData();
            List<String> activeSources = crontabLoader.getActiveSources();
            int jobCount = crontabLoader.getJobCount();
            
            healthStatus.setAvailableSources(activeSources);
            healthStatus.addDetail("crontabLoader", "UP");
            healthStatus.addDetail("activeSources", activeSources);
            healthStatus.addDetail("jobCount", jobCount);
            
            // Check Groovy JAR adapter
            try {
                // Validate a simple cron expression to test Groovy JAR
                boolean isValid = groovyJarAdapter.validateCronExpression("0 0 * * *");
                healthStatus.addDetail("groovyJar", isValid ? "UP" : "DOWN");
            } catch (Exception e) {
                logger.warn("Groovy JAR health check failed", e);
                healthStatus.addDetail("groovyJar", "DOWN");
                healthStatus.addDetail("groovyJarError", e.getMessage());
            }
            
            // Determine overall status
            if (activeSources.isEmpty()) {
                healthStatus.setStatus(HealthStatus.Status.DOWN);
                healthStatus.addDetail("message", "No crontab sources available");
            } else if (healthStatus.getDetails().get("groovyJar").equals("DOWN")) {
                healthStatus.setStatus(HealthStatus.Status.DEGRADED);
                healthStatus.addDetail("message", "Groovy JAR integration unavailable");
            } else {
                healthStatus.setStatus(HealthStatus.Status.UP);
                healthStatus.addDetail("message", "All components operational");
            }
            
            logger.debug("Health check completed: {}", healthStatus.getStatus());
            
        } catch (Exception e) {
            logger.error("Health check failed", e);
            healthStatus.setStatus(HealthStatus.Status.DOWN);
            healthStatus.addDetail("error", e.getMessage());
            healthStatus.addDetail("message", "Service health check failed");
        }
        
        return healthStatus;
    }
    
    /**
     * Validate the query request.
     * 
     * @param request The request to validate
     * @throws InvalidQueryException if the request is invalid
     */
    private void validateRequest(QueryRequest request) {
        if (request == null) {
            throw new InvalidQueryException("Query request cannot be null");
        }
        
        // Check if at least one query parameter is provided
        if (!request.isNaturalLanguageQuery() && !request.isStructuredQuery()) {
            throw new InvalidQueryException(
                "At least one query parameter must be provided (query, day, time, or timeRange)"
            );
        }
        
        // Validate that natural language and structured queries are not mixed
        if (request.isNaturalLanguageQuery() && request.isStructuredQuery()) {
            logger.warn("Both natural language and structured query parameters provided, " +
                       "natural language query will take precedence");
        }
    }
    
    /**
     * Build a human-readable query string from the request.
     * 
     * @param request The query request
     * @return A string representation of the query
     */
    private String buildQueryString(QueryRequest request) {
        if (request.isNaturalLanguageQuery()) {
            return request.getQuery();
        }
        
        StringBuilder queryString = new StringBuilder();
        
        if (request.getDay() != null && !request.getDay().trim().isEmpty()) {
            queryString.append("day=").append(request.getDay());
        }
        
        if (request.getTime() != null && !request.getTime().trim().isEmpty()) {
            if (queryString.length() > 0) {
                queryString.append(", ");
            }
            queryString.append("time=").append(request.getTime());
        }
        
        if (request.getTimeRange() != null && !request.getTimeRange().trim().isEmpty()) {
            if (queryString.length() > 0) {
                queryString.append(", ");
            }
            queryString.append("timeRange=").append(request.getTimeRange());
        }
        
        return queryString.length() > 0 ? queryString.toString() : "all jobs";
    }
}
