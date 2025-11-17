package com.cronquery.service.exception;

/**
 * Exception thrown when Groovy JAR integration fails.
 * This includes errors from invoking Groovy classes, parsing failures,
 * or any other issues related to the Groovy JAR integration.
 */
public class GroovyJarException extends CronQueryException {

    public GroovyJarException(String message) {
        super(message);
    }

    public GroovyJarException(String message, Throwable cause) {
        super(message, cause);
    }
}
