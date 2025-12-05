package com.cronquery.service.integration;

import com.cronquery.service.exception.GroovyJarException;
import com.cronquery.service.exception.InvalidQueryException;
import com.cronquery.service.model.CronJob;
import com.cronquery.service.model.QueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of GroovyJarAdapter that directly invokes Groovy JAR classes.
 * 
 * This adapter provides integration with the cron-query Groovy implementation
 * by invoking its static methods and converting between model types.
 */
@Component
public class GroovyJarAdapterImpl implements GroovyJarAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GroovyJarAdapterImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    @Override
    public List<CronJob> queryJobs(QueryRequest request, String crontabContent) {
        logger.debug("Querying jobs with request: {}", request);
        
        try {
            // Load all jobs from crontab content
            List<com.cronquery.CronJob> groovyJobs = loadGroovyJobs(crontabContent);
            
            if (groovyJobs.isEmpty()) {
                logger.info("No cron jobs found in crontab content");
                return new ArrayList<>();
            }
            
            // Build query string from request
            String queryString = buildQueryString(request);
            logger.debug("Built query string: {}", queryString);
            
            // Parse query using Groovy QueryParser
            com.cronquery.QueryCriteria criteria;
            try {
                criteria = com.cronquery.QueryParser.parseQuery(queryString);
            } catch (Exception e) {
                // Catch any exception from Groovy (QueryParseException is a Groovy exception)
                if (e.getClass().getName().contains("QueryParseException")) {
                    throw new InvalidQueryException("Failed to parse query: " + e.getMessage(), e);
                }
                throw e;
            }
            
            // Check if query was understood
            if (criteria.getQueryType() == com.cronquery.QueryType.UNKNOWN) {
                throw new InvalidQueryException("Could not understand query: " + queryString);
            }
            
            logger.debug("Parsed query criteria: {}", 
                com.cronquery.QueryParser.formatCriteriaDescription(criteria));
            
            // Find matching jobs using Groovy ScheduleAnalyzer
            List<com.cronquery.CronJob> matchingGroovyJobs;
            try {
                matchingGroovyJobs = com.cronquery.ScheduleAnalyzer.findMatchingJobs(groovyJobs, criteria);
            } catch (Exception e) {
                // Catch any exception from Groovy (ScheduleAnalysisException is a Groovy exception)
                if (e.getClass().getName().contains("ScheduleAnalysisException")) {
                    throw new GroovyJarException("Failed to analyze schedules: " + e.getMessage(), e);
                }
                throw e;
            }
            
            logger.info("Found {} matching jobs out of {}", matchingGroovyJobs.size(), groovyJobs.size());
            
            // Convert to Spring Boot model
            return matchingGroovyJobs.stream()
                .map(this::convertToCronJob)
                .collect(Collectors.toList());
                
        } catch (InvalidQueryException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error querying jobs", e);
            throw new GroovyJarException("Failed to query jobs: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CronJob> loadAllJobs(String crontabContent) {
        logger.debug("Loading all jobs from crontab content");
        
        try {
            List<com.cronquery.CronJob> groovyJobs = loadGroovyJobs(crontabContent);
            
            logger.info("Loaded {} cron jobs", groovyJobs.size());
            
            return groovyJobs.stream()
                .map(this::convertToCronJob)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Error loading jobs", e);
            throw new GroovyJarException("Failed to load jobs: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean validateCronExpression(String cronExpression) {
        try {
            // Create a temporary CronJob to validate the expression
            String testLine = cronExpression + " /bin/true";
            com.cronquery.CronJob job = com.cronquery.CronLoader.parseCronLine(testLine, "test", null);
            return job != null && job.isValid();
        } catch (Exception e) {
            logger.debug("Invalid cron expression: {}", cronExpression, e);
            return false;
        }
    }

    /**
     * Load Groovy CronJob objects from crontab content.
     */
    private List<com.cronquery.CronJob> loadGroovyJobs(String crontabContent) {
        List<com.cronquery.CronJob> jobs = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new StringReader(crontabContent))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    com.cronquery.CronJob job = com.cronquery.CronLoader.parseCronLine(
                        line, "system", null);
                    
                    if (job != null) {
                        jobs.add(job);
                        logger.debug("Parsed job from line {}: {}", lineNumber, job.getRawLine());
                    }
                } catch (Exception e) {
                    // Catch any exception from Groovy (CronParseException is a Groovy exception)
                    logger.warn("Failed to parse line {}: {}", lineNumber, e.getMessage());
                    // Continue processing other lines
                }
            }
        } catch (Exception e) {
            throw new GroovyJarException("Error reading crontab content: " + e.getMessage(), e);
        }
        
        return jobs;
    }

    /**
     * Build a query string from QueryRequest parameters.
     */
    private String buildQueryString(QueryRequest request) {
        // If natural language query is provided, use it directly
        if (request.isNaturalLanguageQuery()) {
            return request.getQuery();
        }
        
        // Build structured query from parameters
        StringBuilder query = new StringBuilder();
        
        if (request.getDay() != null && !request.getDay().trim().isEmpty()) {
            query.append("jobs on ").append(request.getDay());
        }
        
        if (request.getTime() != null && !request.getTime().trim().isEmpty()) {
            if (query.length() > 0) {
                query.append(" at ");
            } else {
                query.append("jobs at ");
            }
            query.append(convertTo12HourFormat(request.getTime()));
        }
        
        if (request.getTimeRange() != null && !request.getTimeRange().trim().isEmpty()) {
            String[] times = request.getTimeRange().split("-");
            if (times.length == 2) {
                if (query.length() > 0) {
                    query.append(" between ");
                } else {
                    query.append("jobs between ");
                }
                query.append(convertTo12HourFormat(times[0]))
                     .append(" and ")
                     .append(convertTo12HourFormat(times[1]));
            }
        }
        
        String queryString = query.toString().trim();
        if (queryString.isEmpty()) {
            throw new InvalidQueryException("No query parameters provided");
        }
        
        return queryString;
    }

    /**
     * Convert 24-hour time format to 12-hour format for query parsing.
     */
    private String convertTo12HourFormat(String time24) {
        try {
            String[] parts = time24.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            
            String ampm = hour >= 12 ? "PM" : "AM";
            int hour12 = hour % 12;
            if (hour12 == 0) hour12 = 12;
            
            if (minute == 0) {
                return hour12 + " " + ampm;
            } else {
                return String.format("%d:%02d %s", hour12, minute, ampm);
            }
        } catch (Exception e) {
            logger.warn("Failed to convert time format: {}", time24, e);
            return time24; // Return as-is if conversion fails
        }
    }

    /**
     * Convert Groovy CronJob to Spring Boot CronJob model.
     */
    private CronJob convertToCronJob(com.cronquery.CronJob groovyJob) {
        CronJob cronJob = new CronJob();
        
        // Set basic fields
        cronJob.setSchedule(groovyJob.getCronExpression());
        cronJob.setCommand(groovyJob.getCommand());
        cronJob.setSource(groovyJob.getSource());
        cronJob.setUser(groovyJob.getUser());
        
        // Generate human-readable description
        cronJob.setDescription(generateDescription(groovyJob));
        
        // Calculate next run times
        try {
            List<ZonedDateTime> nextRuns = com.cronquery.ScheduleAnalyzer.getNextRuns(groovyJob, 3, null);
            List<String> nextRunStrings = nextRuns.stream()
                .map(dt -> dt.format(DATE_TIME_FORMATTER))
                .collect(Collectors.toList());
            cronJob.setNextRuns(nextRunStrings);
        } catch (Exception e) {
            logger.warn("Failed to calculate next runs for job: {}", groovyJob.getRawLine(), e);
            cronJob.setNextRuns(new ArrayList<>());
        }
        
        return cronJob;
    }

    /**
     * Generate a human-readable description of the cron schedule.
     */
    private String generateDescription(com.cronquery.CronJob groovyJob) {
        try {
            // Use cron expression to generate description
            String expr = groovyJob.getCronExpression();
            String[] fields = expr.split("\\s+");
            
            if (fields.length < 5) {
                return expr;
            }
            
            StringBuilder desc = new StringBuilder();
            
            // Minute field
            if (!fields[0].equals("*")) {
                desc.append("At minute ").append(fields[0]);
            }
            
            // Hour field
            if (!fields[1].equals("*")) {
                if (desc.length() > 0) desc.append(" ");
                desc.append("past hour ").append(fields[1]);
            }
            
            // Day of month field
            if (!fields[2].equals("*")) {
                if (desc.length() > 0) desc.append(" ");
                desc.append("on day ").append(fields[2]);
            }
            
            // Month field
            if (!fields[3].equals("*")) {
                if (desc.length() > 0) desc.append(" ");
                desc.append("in month ").append(fields[3]);
            }
            
            // Day of week field
            if (!fields[4].equals("*")) {
                if (desc.length() > 0) desc.append(" ");
                desc.append("on day-of-week ").append(fields[4]);
            }
            
            return desc.length() > 0 ? desc.toString() : expr;
            
        } catch (Exception e) {
            logger.debug("Failed to generate description for: {}", groovyJob.getCronExpression(), e);
            return groovyJob.getCronExpression();
        }
    }
}
