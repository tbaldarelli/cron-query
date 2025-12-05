package com.cronquery.service.exception;

/**
 * Exception thrown when a client provides invalid query parameters.
 * This maps to HTTP 400 Bad Request responses.
 */
public class InvalidQueryException extends CronQueryException {

    public InvalidQueryException(String message) {
        super(message);
    }

    public InvalidQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
