package com.cronquery.service.model;

/**
 * Supported output formats for query responses.
 */
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
