package com.cronquery.service;

import com.cronquery.service.model.CronJob;
import com.cronquery.service.model.HealthStatus;
import com.cronquery.service.model.QueryRequest;
import com.cronquery.service.model.QueryResponse;

import java.util.List;

/**
 * Service interface for cron job query operations.
 * 
 * This service orchestrates the business logic for querying cron jobs,
 * coordinating between crontab loading and Groovy JAR integration.
 */
public interface CronQueryService {
    
    /**
     * Execute a query against the loaded crontab data.
     * 
     * @param request The query request containing natural language or structured parameters
     * @return QueryResponse with matching jobs and execution metadata
     * @throws com.cronquery.service.exception.InvalidQueryException if the query is invalid
     * @throws com.cronquery.service.exception.CrontabLoadException if crontab data cannot be loaded
     * @throws com.cronquery.service.exception.GroovyJarException if query execution fails
     */
    QueryResponse executeQuery(QueryRequest request);
    
    /**
     * Get all cron jobs from all available sources.
     * 
     * @return List of all cron jobs
     * @throws com.cronquery.service.exception.CrontabLoadException if crontab data cannot be loaded
     */
    List<CronJob> getAllJobs();
    
    /**
     * Check the health status of the service and its dependencies.
     * 
     * @return HealthStatus with component health information
     */
    HealthStatus checkHealth();
}
