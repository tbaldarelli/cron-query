package com.cronquery.service.exception;

/**
 * Exception thrown when crontab data cannot be loaded from any source.
 * This maps to HTTP 500 Internal Server Error responses.
 */
public class CrontabLoadException extends CronQueryException {

    public CrontabLoadException(String message) {
        super(message);
    }

    public CrontabLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
