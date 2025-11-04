package com.cronquery

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.fusesource.jansi.Ansi

import static org.fusesource.jansi.Ansi.ansi

/**
 * Supported output formats.
 */
@CompileStatic
enum OutputFormat {
    LIST,
    TABLE,
    JSON,
    YAML
}

/**
 * Exception raised when output formatting fails.
 */
class OutputFormatterException extends Exception {
    OutputFormatterException(String message) {
        super(message)
    }
    
    OutputFormatterException(String message, Throwable cause) {
        super(message, cause)
    }
}

/**
 * Output Formatter - Formats query results for user-friendly display.
 * 
 * This class provides functionality to format cron job query results
 * in various output formats with human-readable descriptions.
 */
@Slf4j
@CompileStatic
class OutputFormatter {
    
    private boolean useColors
    private boolean showNextRuns
    private int maxNextRuns
    
    OutputFormatter(boolean useColors = true, boolean showNextRuns = true, int maxNextRuns = 3) {
        this.useColors = useColors && isColorSupported()
        this.showNextRuns = showNextRuns
        this.maxNextRuns = maxNextRuns
    }
    
    /**
     * Check if color output is supported.
     */
    private static boolean isColorSupported() {
        // Check if we're running in a terminal that supports colors
        return System.console() != null || 
               System.getenv('TERM') != null ||
               System.getProperty('os.name').toLowerCase().contains('windows')
    }
    
    /**
     * Format query results for display.
     */
    String formatQueryResults(
        List<CronJob> jobs,
        QueryCriteria criteria,
        String outputFormat = 'list'
    ) {
        log.debug("Formatting ${jobs.size()} jobs in ${outputFormat} format")
        
        try {
            switch (outputFormat.toLowerCase()) {
                case 'list':
                    return formatListOutput(jobs, criteria)
                case 'table':
                    return formatTableOutput(jobs, criteria)
                case 'json':
                    return formatJsonOutput(jobs, criteria)
                case 'yaml':
                    return formatYamlOutput(jobs, criteria)
                default:
                    throw new OutputFormatterException("Unsupported output format: ${outputFormat}")
            }
        } catch (Exception e) {
            throw new OutputFormatterException("Failed to format output: ${e.message}", e)
        }
    }
    
    /**
     * Format results as a list (human-readable).
     */
    private String formatListOutput(List<CronJob> jobs, QueryCriteria criteria) {
        if (!jobs) {
            return formatEmptyResults(criteria)
        }
        
        StringBuilder output = new StringBuilder()
        
        // Header
        String criteriaDesc = QueryParser.formatCriteriaDescription(criteria)
        output.append(colorize("\nFound ${jobs.size()} job(s) matching: ${criteriaDesc}\n", 'header'))
        output.append(colorize('=' * 70, 'header')).append('\n\n')
        
        // Format each job
        jobs.eachWithIndex { job, index ->
            output.append(colorize("Job ${index + 1}:", 'header')).append('\n')
            output.append(colorize("  Cron:    ", 'label'))
            output.append(colorize(job.cronExpression, 'cron')).append('\n')
            output.append(colorize("  Command: ", 'label'))
            output.append(colorize(job.command, 'command')).append('\n')
            
            if (job.user) {
                output.append(colorize("  User:    ", 'label'))
                output.append(job.user).append('\n')
            }
            
            if (showNextRuns) {
                try {
                    def nextRuns = ScheduleAnalyzer.getNextRuns(job, maxNextRuns)
                    if (nextRuns) {
                        output.append(colorize("  Next runs:", 'label')).append('\n')
                        nextRuns.each { run ->
                            output.append("    - ${formatDateTime(run)}\n")
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not calculate next runs: ${e.message}")
                }
            }
            
            output.append('\n')
        }
        
        return output.toString()
    }
    
    /**
     * Format results as a table.
     */
    private String formatTableOutput(List<CronJob> jobs, QueryCriteria criteria) {
        if (!jobs) {
            return formatEmptyResults(criteria)
        }
        
        StringBuilder output = new StringBuilder()
        
        // Header
        String criteriaDesc = QueryParser.formatCriteriaDescription(criteria)
        output.append(colorize("\nFound ${jobs.size()} job(s) matching: ${criteriaDesc}\n", 'header'))
        output.append(colorize('=' * 100, 'header')).append('\n\n')
        
        // Table header
        String headerLine = String.format('%-20s %-10s %-50s', 'CRON EXPRESSION', 'USER', 'COMMAND')
        output.append(colorize(headerLine, 'header')).append('\n')
        output.append(colorize('-' * 100, 'header')).append('\n')
        
        // Table rows
        jobs.each { job ->
            String cronExpr = truncate(job.cronExpression, 20)
            String user = truncate(job.user ?: 'current', 10)
            String command = truncate(job.command, 50)
            
            output.append(String.format('%-20s %-10s %-50s\n', 
                colorize(cronExpr, 'cron'),
                user,
                colorize(command, 'command')
            ))
        }
        
        output.append('\n')
        return output.toString()
    }
    
    /**
     * Format results as JSON.
     */
    private String formatJsonOutput(List<CronJob> jobs, QueryCriteria criteria) {
        List<Map<String, Object>> jobData = jobs.collect { job -> 
            return createJobMap(job)
        } as List<Map<String, Object>>
        
        Map<String, Object> result = [
            query: criteria.rawQuery,
            matches: jobs.size(),
            jobs: jobData
        ]
        
        return JsonOutput.prettyPrint(JsonOutput.toJson(result))
    }
    
    /**
     * Create a map representation of a cron job for JSON output.
     */
    private Map<String, Object> createJobMap(CronJob job) {
        Map<String, Object> data = [
            cron_expression: job.cronExpression,
            command: job.command,
            user: job.user,
            source: job.source,
            raw_line: job.rawLine
        ]
        
        if (showNextRuns) {
            try {
                def nextRuns = ScheduleAnalyzer.getNextRuns(job, maxNextRuns)
                data.next_runs = nextRuns.collect { it.toString() }
            } catch (Exception e) {
                log.debug("Could not calculate next runs: ${e.message}")
            }
        }
        
        return data
    }
    
    /**
     * Format results as YAML (simplified - just uses JSON-like structure).
     */
    private String formatYamlOutput(List<CronJob> jobs, QueryCriteria criteria) {
        // Simplified YAML output - for full YAML, would need external library
        StringBuilder output = new StringBuilder()
        
        output.append("query: \"${criteria.rawQuery}\"\n")
        output.append("matches: ${jobs.size()}\n")
        output.append("jobs:\n")
        
        jobs.each { job ->
            output.append("  - cron_expression: \"${job.cronExpression}\"\n")
            output.append("    command: \"${job.command}\"\n")
            output.append("    user: \"${job.user ?: 'current'}\"\n")
            output.append("    source: \"${job.source}\"\n")
            
            if (showNextRuns) {
                try {
                    def nextRuns = ScheduleAnalyzer.getNextRuns(job, maxNextRuns)
                    if (nextRuns) {
                        output.append("    next_runs:\n")
                        nextRuns.each { run ->
                            output.append("      - \"${run}\"\n")
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        
        return output.toString()
    }
    
    /**
     * Format empty results message.
     */
    private String formatEmptyResults(QueryCriteria criteria) {
        StringBuilder output = new StringBuilder()
        
        String criteriaDesc = QueryParser.formatCriteriaDescription(criteria)
        output.append(colorize('\nNo jobs found matching: ', 'warning'))
        output.append(colorize(criteriaDesc, 'header')).append('\n')
        
        return output.toString()
    }
    
    /**
     * Format error message.
     */
    static String formatErrorMessage(String message) {
        Ansi ansi = ansi().fgBrightRed().a('ERROR: ').reset().a(message)
        return ansi.toString()
    }
    
    /**
     * Apply color to text based on type.
     */
    private String colorize(String text, String type) {
        if (!useColors) {
            return text
        }
        
        Ansi ansi = ansi()
        
        switch (type) {
            case 'header':
                ansi.fgBright(Ansi.Color.WHITE).bold()
                break
            case 'cron':
                ansi.fgBright(Ansi.Color.CYAN)
                break
            case 'command':
                ansi.fgBright(Ansi.Color.GREEN)
                break
            case 'label':
                ansi.fg(Ansi.Color.YELLOW)
                break
            case 'warning':
                ansi.fgBright(Ansi.Color.YELLOW)
                break
            case 'error':
                ansi.fgBright(Ansi.Color.RED)
                break
            default:
                return text
        }
        
        return ansi.a(text).reset().toString()
    }
    
    /**
     * Truncate string to maximum length.
     */
    private static String truncate(String text, int maxLength) {
        if (!text || text.length() <= maxLength) {
            return text ?: ''
        }
        return text.substring(0, maxLength - 3) + '...'
    }
    
    /**
     * Format date/time for display.
     */
    private static String formatDateTime(def dateTime) {
        // Simple formatting - could be enhanced
        return dateTime.toString()
    }
    
    /**
     * Get list of supported formats.
     */
    static List<String> getSupportedFormats() {
        return ['list', 'table', 'json', 'yaml']
    }
    
    /**
     * Validate output format.
     */
    static boolean validateOutputFormat(String format) {
        return format.toLowerCase() in getSupportedFormats()
    }
}
