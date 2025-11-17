package com.cronquery.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Response model for cron job queries.
 * Contains the list of matching jobs and metadata about the query execution.
 */
@Schema(description = "Response containing matching cron jobs and query metadata")
public class QueryResponse {

    @Schema(description = "List of matching cron jobs")
    private List<CronJob> jobs;

    @Schema(description = "Total number of matching jobs", example = "5")
    private int totalCount;

    @Schema(description = "Original query string", example = "jobs on Saturday")
    private String query;

    @Schema(description = "Crontab sources used for the query")
    private List<String> sources;

    @Schema(description = "Query execution time in milliseconds", example = "45")
    private long executionTimeMs;

    // Constructors
    public QueryResponse() {
        this.jobs = new ArrayList<>();
        this.sources = new ArrayList<>();
    }

    public QueryResponse(List<CronJob> jobs, int totalCount, String query, 
                        List<String> sources, long executionTimeMs) {
        this.jobs = jobs != null ? jobs : new ArrayList<>();
        this.totalCount = totalCount;
        this.query = query;
        this.sources = sources != null ? sources : new ArrayList<>();
        this.executionTimeMs = executionTimeMs;
    }

    // Getters and Setters
    public List<CronJob> getJobs() {
        return jobs;
    }

    public void setJobs(List<CronJob> jobs) {
        this.jobs = jobs != null ? jobs : new ArrayList<>();
        this.totalCount = this.jobs.size();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources != null ? sources : new ArrayList<>();
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    @Override
    public String toString() {
        return "QueryResponse{" +
                "totalCount=" + totalCount +
                ", query='" + query + '\'' +
                ", sources=" + sources +
                ", executionTimeMs=" + executionTimeMs +
                '}';
    }
}
