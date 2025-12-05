package com.cronquery.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Standard error response model for API errors.
 */
@Schema(description = "Error response with details about the failure")
public class ErrorResponse {

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Error type", example = "Bad Request")
    private String error;

    @Schema(description = "Detailed error message", 
            example = "Invalid query parameter: time must be in HH:MM format")
    private String message;

    @Schema(description = "Request path that caused the error", 
            example = "/api/jobs")
    private String path;

    @Schema(description = "Timestamp when the error occurred", 
            example = "1699876543210")
    private long timestamp;

    // Constructors
    public ErrorResponse() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public ErrorResponse(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.timestamp = Instant.now().toEpochMilli();
    }

    // Getters and Setters
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "status=" + status +
                ", error='" + error + '\'' +
                ", message='" + message + '\'' +
                ", path='" + path + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
