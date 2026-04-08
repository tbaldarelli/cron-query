package com.cronquery.service.parser;

import com.cronquery.service.exception.CronParseException;
import com.cronutils.model.Cron;

public interface CronParser {
    /**
     * Parse a cron expression into a structured representation.
     * 
     * @param cronExpression 5-field cron expression (minute hour dom month dow)
     * @return Parsed Cron object from cron-utils
     * @throws CronParseException if expression is invalid
     */
    Cron parse(String cronExpression) throws CronParseException;

    /**
     * Validate a cron expression without parsing.
     * 
     * @param cronExpression Expression to validate
     * @return true if valid, false otherwise
     */
    boolean validate(String cronExpression);

    /**
     * Format a parsed cron expression back to string.
     * 
     * @param cron Parsed Cron object
     * @return Formatted cron expression string
     */
    String format(Cron cron);
}
