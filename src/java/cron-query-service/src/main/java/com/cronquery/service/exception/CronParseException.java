package com.cronquery.service.exception;

/**
 * Exception thrown when a cron expression cannot be parsed.
 * Includes the invalid expression for debugging and error reporting.
 */
public class CronParseException extends CronQueryException {

    private final String invalidExpression;

    public CronParseException(String message, String invalidExpression) {
        super(message);
        this.invalidExpression = invalidExpression;
    }

    public CronParseException(String message, String invalidExpression, Throwable cause) {
        super(message, cause);
        this.invalidExpression = invalidExpression;
    }

    public String getInvalidExpression() {
        return invalidExpression;
    }
}
