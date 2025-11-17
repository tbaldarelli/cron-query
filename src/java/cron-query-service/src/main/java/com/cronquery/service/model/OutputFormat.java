package com.cronquery.service.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Supported output formats for query responses.
 */
@Schema(description = "Output format for query results", enumAsRef = true)
public enum OutputFormat {
    JSON,
    CSV,
    YAML;

    /**
     * Parse output format from string, case-insensitive.
     * Returns JSON as default if invalid.
     */
    public static OutputFormat fromString(String format) {
        if (format == null || format.trim().isEmpty()) {
            return JSON;
        }
        try {
            return OutputFormat.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            return JSON;
        }
    }
}
