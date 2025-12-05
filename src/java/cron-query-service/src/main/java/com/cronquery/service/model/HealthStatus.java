package com.cronquery.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Health status model for service health checks.
 */
@Schema(description = "Health status of the service and its components")
public class HealthStatus {

    @Schema(description = "Overall health status", 
            example = "UP",
            allowableValues = {"UP", "DOWN", "DEGRADED"})
    private Status status;

    @Schema(description = "Additional health details and component statuses")
    private Map<String, Object> details;

    @Schema(description = "List of available crontab sources")
    private List<String> availableSources;

    // Constructors
    public HealthStatus() {
        this.details = new HashMap<>();
        this.availableSources = new ArrayList<>();
    }

    public HealthStatus(Status status, Map<String, Object> details, List<String> availableSources) {
        this.status = status;
        this.details = details != null ? details : new HashMap<>();
        this.availableSources = availableSources != null ? availableSources : new ArrayList<>();
    }

    // Getters and Setters
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details != null ? details : new HashMap<>();
    }

    public void addDetail(String key, Object value) {
        this.details.put(key, value);
    }

    public List<String> getAvailableSources() {
        return availableSources;
    }

    public void setAvailableSources(List<String> availableSources) {
        this.availableSources = availableSources != null ? availableSources : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "HealthStatus{" +
                "status=" + status +
                ", availableSources=" + availableSources +
                ", details=" + details +
                '}';
    }

    /**
     * Health status enumeration.
     */
    public enum Status {
        UP,
        DOWN,
        DEGRADED
    }
}
