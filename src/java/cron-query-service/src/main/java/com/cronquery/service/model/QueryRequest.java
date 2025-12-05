package com.cronquery.service.model;

import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request model for cron job queries.
 * Supports both natural language queries and structured query parameters.
 */
@Schema(description = "Request parameters for querying cron jobs")
public class QueryRequest {

    @Schema(description = "Natural language query (e.g., 'jobs on weekends', 'what runs at 8 AM')", 
            example = "jobs on Saturday")
    private String query;

    @Schema(description = "Day filter (day name or date)", 
            example = "Monday")
    private String day;

    @Schema(description = "Time filter in HH:MM format", 
            example = "08:00",
            pattern = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$")
    @Pattern(regexp = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$", 
             message = "Time must be in HH:MM format")
    private String time;

    @Schema(description = "Time range filter in HH:MM-HH:MM format", 
            example = "08:00-17:00",
            pattern = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]-([0-1]?[0-9]|2[0-3]):[0-5][0-9]$")
    @Pattern(regexp = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]-([0-1]?[0-9]|2[0-3]):[0-5][0-9]$", 
             message = "Time range must be in HH:MM-HH:MM format")
    private String timeRange;

    @Schema(description = "Output format", 
            example = "json",
            allowableValues = {"json", "csv", "yaml"})
    private OutputFormat format = OutputFormat.JSON;

    // Constructors
    public QueryRequest() {
    }

    public QueryRequest(String query, String day, String time, String timeRange, OutputFormat format) {
        this.query = query;
        this.day = day;
        this.time = time;
        this.timeRange = timeRange;
        this.format = format != null ? format : OutputFormat.JSON;
    }

    // Getters and Setters
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        this.timeRange = timeRange;
    }

    public OutputFormat getFormat() {
        return format;
    }

    public void setFormat(OutputFormat format) {
        this.format = format != null ? format : OutputFormat.JSON;
    }

    /**
     * Check if this is a natural language query.
     */
    public boolean isNaturalLanguageQuery() {
        return query != null && !query.trim().isEmpty();
    }

    /**
     * Check if this is a structured query.
     */
    public boolean isStructuredQuery() {
        return (day != null && !day.trim().isEmpty()) 
            || (time != null && !time.trim().isEmpty()) 
            || (timeRange != null && !timeRange.trim().isEmpty());
    }

    @Override
    public String toString() {
        return "QueryRequest{" +
                "query='" + query + '\'' +
                ", day='" + day + '\'' +
                ", time='" + time + '\'' +
                ", timeRange='" + timeRange + '\'' +
                ", format=" + format +
                '}';
    }
}
