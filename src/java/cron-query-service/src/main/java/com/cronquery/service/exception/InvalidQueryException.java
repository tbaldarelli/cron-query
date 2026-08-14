package com.cronquery.service.exception;

/**
 * Exception thrown when a client provides invalid query parameters.
 * This maps to HTTP 400 Bad Request responses.
 */
public class InvalidQueryException extends CronQueryException {

    private final String invalidQuery;

    // Legacy constructors for backward compatibility
    public InvalidQueryException(String message) {
        super(message);
        this.invalidQuery = null;
    }

    public InvalidQueryException(String message, Throwable cause) {
        super(message, cause);
        this.invalidQuery = null;
    }

    // New constructors with context
    public InvalidQueryException(String message, String invalidQuery) {
        super(message);
        this.invalidQuery = invalidQuery;
    }

    public InvalidQueryException(String message, String invalidQuery, Throwable cause) {
        super(message, cause);
        this.invalidQuery = invalidQuery;
    }

    public String getInvalidQuery() {
        return invalidQuery;
    }
}
