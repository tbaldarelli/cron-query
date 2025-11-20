package com.cronquery.service.controller;

import com.cronquery.service.exception.CronQueryException;
import com.cronquery.service.exception.CrontabLoadException;
import com.cronquery.service.exception.GroovyJarException;
import com.cronquery.service.exception.InvalidQueryException;
import com.cronquery.service.model.ErrorResponse;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Global exception handler for the cron-query service.
 * Maps exceptions to appropriate HTTP status codes and formats error responses consistently.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    private final MeterRegistry meterRegistry;
    
    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Handle InvalidQueryException - client errors (HTTP 400).
     */
    @ExceptionHandler(InvalidQueryException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleInvalidQueryException(
            InvalidQueryException ex, 
            HttpServletRequest request) {
        
        logger.warn("Invalid query request: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        // Track error metric
        meterRegistry.counter("cronquery.errors.total", 
            "type", "InvalidQueryException",
            "status", "400").increment();
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle CrontabLoadException - server errors (HTTP 500).
     */
    @ExceptionHandler(CrontabLoadException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleCrontabLoadException(
            CrontabLoadException ex, 
            HttpServletRequest request) {
        
        logger.error("Failed to load crontab data: {} - Path: {}", ex.getMessage(), request.getRequestURI(), ex);
        
        // Track error metric
        meterRegistry.counter("cronquery.errors.total", 
            "type", "CrontabLoadException",
            "status", "500").increment();
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Failed to load crontab data: " + ex.getMessage(),
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle GroovyJarException - server errors (HTTP 500).
     */
    @ExceptionHandler(GroovyJarException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleGroovyJarException(
            GroovyJarException ex, 
            HttpServletRequest request) {
        
        logger.error("Groovy JAR integration error: {} - Path: {}", ex.getMessage(), request.getRequestURI(), ex);
        
        // Track error metric
        meterRegistry.counter("cronquery.errors.total", 
            "type", "GroovyJarException",
            "status", "500").increment();
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Query processing failed: " + ex.getMessage(),
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle generic CronQueryException - server errors (HTTP 500).
     */
    @ExceptionHandler(CronQueryException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleCronQueryException(
            CronQueryException ex, 
            HttpServletRequest request) {
        
        logger.error("Cron query error: {} - Path: {}", ex.getMessage(), request.getRequestURI(), ex);
        
        // Track error metric
        meterRegistry.counter("cronquery.errors.total", 
            "type", "CronQueryException",
            "status", "500").increment();
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An error occurred processing your request: " + ex.getMessage(),
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle all other unexpected exceptions - server errors (HTTP 500).
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, 
            HttpServletRequest request) {
        
        logger.error("Unexpected error: {} - Path: {}", ex.getMessage(), request.getRequestURI(), ex);
        
        // Track error metric
        meterRegistry.counter("cronquery.errors.total", 
            "type", "UnexpectedException",
            "status", "500").increment();
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
