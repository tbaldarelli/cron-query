package com.cronquery.service.exception;

/**
 * Exception thrown when schedule analysis fails.
 * This maps to HTTP 500 Internal Server Error responses.
 */
public class ScheduleAnalysisException extends CronQueryException {

    public ScheduleAnalysisException(String message) {
        super(message);
    }

    public ScheduleAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
