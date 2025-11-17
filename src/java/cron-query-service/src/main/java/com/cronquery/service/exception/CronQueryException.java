package com.cronquery.service.exception;

/**
 * Base exception for all cron-query service errors.
 * Provides a common parent for all custom exceptions in the application.
 */
public class CronQueryException extends RuntimeException {

    public CronQueryException(String message) {
        super(message);
    }

    public CronQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
