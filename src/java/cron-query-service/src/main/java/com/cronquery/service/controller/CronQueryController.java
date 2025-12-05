package com.cronquery.service.controller;

import com.cronquery.service.CronQueryService;
import com.cronquery.service.model.CronJob;
import com.cronquery.service.model.OutputFormat;
import com.cronquery.service.model.QueryRequest;
import com.cronquery.service.model.QueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for cron job queries.
 * 
 * Provides endpoints for querying cron schedules using natural language
 * or structured parameters, with support for multiple output formats.
 */
@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Cron Query API", description = "Query and analyze cron job schedules")
public class CronQueryController {

    private static final Logger logger = LoggerFactory.getLogger(CronQueryController.class);
    
    private final CronQueryService cronQueryService;
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;
    private final Counter apiRequestCounter;
    private final Timer apiRequestTimer;

    /**
     * Constructor with dependency injection.
     * 
     * @param cronQueryService The service for executing cron queries
     * @param apiRequestCounter Counter for API requests
     * @param apiRequestTimer Timer for API request duration
     */
    public CronQueryController(CronQueryService cronQueryService,
                               Counter apiRequestCounter,
                               Timer apiRequestTimer) {
        this.cronQueryService = cronQueryService;
        this.jsonMapper = new ObjectMapper();
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.apiRequestCounter = apiRequestCounter;
        this.apiRequestTimer = apiRequestTimer;
    }

    /**
     * Query cron jobs using natural language or structured parameters.
     * 
     * @param query Natural language query (e.g., "jobs on weekends", "what runs at 8 AM")
     * @param day Day filter (day name or date)
     * @param time Time filter in HH:MM format
     * @param timeRange Time range filter in HH:MM-HH:MM format
     * @param format Output format (json, csv, yaml) - defaults to json
     * @return QueryResponse with matching jobs and metadata
     */
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, "text/csv", "application/x-yaml"})
    @Operation(
        summary = "Query cron jobs",
        description = "Query cron jobs using natural language or structured parameters. " +
                     "Supports multiple output formats (JSON, CSV, YAML)."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved matching cron jobs",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = QueryResponse.class),
                examples = @ExampleObject(
                    name = "Weekend jobs example",
                    value = """
                        {
                          "jobs": [
                            {
                              "schedule": "0 8 * * 6,0",
                              "command": "/usr/bin/backup.sh",
                              "source": "/etc/crontab",
                              "user": "root",
                              "nextRuns": ["2024-11-16 08:00:00", "2024-11-17 08:00:00"],
                              "description": "At 08:00 on Saturday and Sunday"
                            }
                          ],
                          "totalCount": 1,
                          "query": "jobs on weekends",
                          "sources": ["/etc/crontab"],
                          "executionTimeMs": 45
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid query parameters",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    name = "Invalid time format",
                    value = """
                        {
                          "status": 400,
                          "error": "Bad Request",
                          "message": "Time must be in HH:MM format",
                          "path": "/api/jobs",
                          "timestamp": 1699876543210
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Server error during query processing",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    name = "Crontab load failure",
                    value = """
                        {
                          "status": 500,
                          "error": "Internal Server Error",
                          "message": "Failed to load crontab data",
                          "path": "/api/jobs",
                          "timestamp": 1699876543210
                        }
                        """
                )
            )
        )
    })
    public ResponseEntity<String> queryJobs(
            @Parameter(
                description = "Natural language query (e.g., 'jobs on weekends', 'what runs at 8 AM')",
                example = "jobs on Saturday"
            )
            @RequestParam(required = false) String query,
            
            @Parameter(
                description = "Day filter (day name like 'Monday' or date)",
                example = "Monday"
            )
            @RequestParam(required = false) String day,
            
            @Parameter(
                description = "Time filter in HH:MM format",
                example = "08:00"
            )
            @RequestParam(required = false) String time,
            
            @Parameter(
                description = "Time range filter in HH:MM-HH:MM format",
                example = "08:00-17:00"
            )
            @RequestParam(required = false) String timeRange,
            
            @Parameter(
                description = "Output format (json, csv, yaml)",
                example = "json",
                schema = @Schema(allowableValues = {"json", "csv", "yaml"})
            )
            @RequestParam(required = false, defaultValue = "json") String format
    ) {
        logger.info("Received query request - query: {}, day: {}, time: {}, timeRange: {}, format: {}", 
                   query, day, time, timeRange, format);
        
        // Track API request
        apiRequestCounter.increment();
        
        // Execute query with timing
        return apiRequestTimer.record(() -> {
            // Build query request
            QueryRequest request = new QueryRequest();
            request.setQuery(query);
            request.setDay(day);
            request.setTime(time);
            request.setTimeRange(timeRange);
            request.setFormat(OutputFormat.fromString(format));
            
            // Execute query
            QueryResponse response = cronQueryService.executeQuery(request);
            
            // Format response based on requested format
            String formattedResponse = formatResponse(response, request.getFormat());
            String contentType = getContentType(request.getFormat());
            
            logger.info("Query completed successfully - found {} jobs in {}ms", 
                       response.getTotalCount(), response.getExecutionTimeMs());
            
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(formattedResponse);
        });
    }

    /**
     * Format the query response based on the requested output format.
     * 
     * @param response The query response to format
     * @param format The desired output format
     * @return Formatted response string
     */
    private String formatResponse(QueryResponse response, OutputFormat format) {
        return switch (format) {
            case CSV -> formatAsCsv(response);
            case YAML -> formatAsYaml(response);
            default -> formatAsJson(response);
        };
    }

    /**
     * Get the content type for the specified output format.
     * 
     * @param format The output format
     * @return Content type string
     */
    private @NonNull String getContentType(OutputFormat format) {
        return switch (format) {
            case CSV -> "text/csv";
            case YAML -> "application/x-yaml";
            default -> MediaType.APPLICATION_JSON_VALUE;
        };
    }

    /**
     * Format response as JSON.
     * 
     * @param response The query response to format
     * @return JSON formatted string
     */
    private String formatAsJson(QueryResponse response) {
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        } catch (Exception e) {
            logger.error("Failed to format response as JSON", e);
            throw new RuntimeException("Failed to format response as JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Format response as CSV.
     * 
     * @param response The query response to format
     * @return CSV formatted string
     */
    private String formatAsCsv(QueryResponse response) {
        StringBuilder csv = new StringBuilder();
        
        // CSV Header
        csv.append("Schedule,Command,Source,User,Description,Next Runs\n");
        
        // CSV Rows
        for (CronJob job : response.getJobs()) {
            csv.append(escapeCsvField(job.getSchedule())).append(",");
            csv.append(escapeCsvField(job.getCommand())).append(",");
            csv.append(escapeCsvField(job.getSource())).append(",");
            csv.append(escapeCsvField(job.getUser())).append(",");
            csv.append(escapeCsvField(job.getDescription())).append(",");
            
            // Format next runs as semicolon-separated list
            if (job.getNextRuns() != null && !job.getNextRuns().isEmpty()) {
                String nextRuns = String.join("; ", job.getNextRuns());
                csv.append(escapeCsvField(nextRuns));
            }
            
            csv.append("\n");
        }
        
        return csv.toString();
    }

    /**
     * Format response as YAML.
     * 
     * @param response The query response to format
     * @return YAML formatted string
     */
    private String formatAsYaml(QueryResponse response) {
        try {
            return yamlMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("Failed to format response as YAML", e);
            throw new RuntimeException("Failed to format response as YAML: " + e.getMessage(), e);
        }
    }

    /**
     * Escape a CSV field by wrapping in quotes if it contains special characters.
     * 
     * @param field The field to escape
     * @return Escaped field
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        
        // If field contains comma, quote, or newline, wrap in quotes and escape internal quotes
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        
        return field;
    }
}
