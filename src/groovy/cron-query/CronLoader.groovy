package com.cronquery

import com.cronutils.model.Cron
import com.cronutils.model.definition.CronDefinition
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.util.logging.Slf4j

import static com.cronutils.model.CronType.UNIX

/**
 * Represents a single cron job with its schedule and command.
 */
@Immutable
@CompileStatic
class CronJob {
    String minute
    String hour
    String dayOfMonth
    String month
    String dayOfWeek
    String command
    String rawLine
    String user
    String source = 'user'
    
    /**
     * Returns the standard 5-field cron expression.
     */
    String getCronExpression() {
        return "${minute} ${hour} ${dayOfMonth} ${month} ${dayOfWeek}"
    }
    
    /**
     * Checks if this cron job has a valid expression.
     */
    boolean isValid() {
        try {
            CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(UNIX)
            CronParser parser = new CronParser(cronDefinition)
            parser.parse(cronExpression)
            return true
        } catch (Exception e) {
            return false
        }
    }
}

/**
 * Exception raised when parsing a cron line fails.
 */
class CronParseException extends Exception {
    CronParseException(String message) {
        super(message)
    }
    
    CronParseException(String message, Throwable cause) {
        super(message, cause)
    }
}

/**
 * Cron Data Loader - Handles loading and parsing crontab data.
 * 
 * This class provides functionality to load crontab data from various sources
 * and parse individual cron lines into structured CronJob objects.
 */
@Slf4j
@CompileStatic
class CronLoader {
    
    // Special cron keywords mapping to standard expressions
    private static final Map<String, String> SPECIAL_KEYWORDS = [
        '@yearly'  : '0 0 1 1 *',
        '@annually': '0 0 1 1 *',
        '@monthly' : '0 0 1 * *',
        '@weekly'  : '0 0 * * 0',
        '@daily'   : '0 0 * * *',
        '@midnight': '0 0 * * *',
        '@hourly'  : '0 * * * *'
    ].asImmutable()
    
    // Common system users for heuristic parsing
    private static final Set<String> SYSTEM_USERS = [
        'root', 'www-data', 'nobody', 'daemon', 'mail', 
        'news', 'uucp', 'proxy', 'backup', 'list', 'man'
    ].asImmutable() as Set<String>
    
    /**
     * Parse a single cron line into a CronJob object.
     * 
     * @param line Raw cron line from crontab
     * @param source Source of the cron job (user, system, etc.)
     * @param user User who owns this cron job
     * @return CronJob object or null if line should be skipped
     * @throws CronParseException If the line cannot be parsed
     */
    static CronJob parseCronLine(String line, String source = 'user', String user = null) {
        String originalLine = line?.trim()
        
        // Skip empty lines and comments
        if (!originalLine || originalLine.startsWith('#')) {
            return null
        }
        
        // Skip environment variable assignments
        if (originalLine.contains('=') && !originalLine.split('=')[0].contains(' ')) {
            log.debug("Skipping environment variable: ${originalLine}")
            return null
        }
        
        try {
            // Handle special keywords
            if (originalLine.startsWith('@')) {
                return parseSpecialKeyword(originalLine, source, user)
            }
            
            // Handle standard cron format
            return parseStandardFormat(originalLine, source, user)
            
        } catch (CronParseException e) {
            throw e
        } catch (Exception e) {
            throw new CronParseException("Failed to parse cron line '${originalLine}': ${e.message}", e)
        }
    }
    
    /**
     * Parse a cron line with special keyword format (@hourly, @daily, etc.)
     */
    private static CronJob parseSpecialKeyword(String line, String source, String user) {
        String[] parts = line.split(/\s+/, 2)
        
        if (parts.length < 2) {
            throw new CronParseException("Invalid special keyword format: ${line}")
        }
        
        String keyword = parts[0].toLowerCase()
        String command = parts[1]
        
        if (!SPECIAL_KEYWORDS.containsKey(keyword)) {
            throw new CronParseException("Unknown special keyword: ${keyword}")
        }
        
        // Convert to standard format
        String cronExpr = SPECIAL_KEYWORDS[keyword]
        String[] fields = cronExpr.split(/\s+/)
        
        return new CronJob(
            minute: fields[0],
            hour: fields[1],
            dayOfMonth: fields[2],
            month: fields[3],
            dayOfWeek: fields[4],
            command: command,
            rawLine: line,
            user: user,
            source: source
        )
    }
    
    /**
     * Parse a cron line with standard format (5 time fields + command)
     */
    private static CronJob parseStandardFormat(String line, String source, String user) {
        String[] parts = line.split(/\s+/)
        
        if (parts.length < 6) {
            throw new CronParseException(
                "Invalid cron format - expected 6 or 7 fields, got ${parts.length}: ${line}"
            )
        }
        
        String minute = parts[0]
        String hour = parts[1]
        String dayOfMonth = parts[2]
        String month = parts[3]
        String dayOfWeek = parts[4]
        
        String parsedUser = user
        String command
        
        if (parts.length == 6) {
            // Standard user crontab format (6 fields)
            command = parts[5]
        } else {
            // Could be standard format with arguments or system format with user field
            String potentialUser = parts[5]
            boolean looksLikePath = potentialUser.startsWith('/') || 
                                   potentialUser.startsWith('./') || 
                                   potentialUser.startsWith('~') || 
                                   potentialUser.contains('=')
            
            if (source == 'system' && parts.length >= 7 && !looksLikePath) {
                // System format: has explicit user field
                parsedUser = potentialUser
                command = parts[6..-1].join(' ')
            } else if (looksLikePath) {
                // Standard format with path command
                command = parts[5..-1].join(' ')
            } else if (potentialUser in SYSTEM_USERS && parts.length >= 7) {
                // Recognized system user
                parsedUser = potentialUser
                command = parts[6..-1].join(' ')
            } else {
                // Default to standard format with arguments
                command = parts[5..-1].join(' ')
            }
        }
        
        // Basic field validation
        validateCronFields(minute, hour, dayOfMonth, month, dayOfWeek)
        
        CronJob cronJob = new CronJob(
            minute: minute,
            hour: hour,
            dayOfMonth: dayOfMonth,
            month: month,
            dayOfWeek: dayOfWeek,
            command: command,
            rawLine: line,
            user: parsedUser,
            source: source
        )
        
        // Validate the complete expression
        if (!cronJob.isValid()) {
            throw new CronParseException("Invalid cron expression: ${cronJob.cronExpression}")
        }
        
        return cronJob
    }
    
    /**
     * Basic validation of cron fields.
     */
    private static void validateCronFields(String minute, String hour, String dayOfMonth, 
                                          String month, String dayOfWeek) {
        Map<String, String> fields = [
            minute: minute,
            hour: hour,
            dayOfMonth: dayOfMonth,
            month: month,
            dayOfWeek: dayOfWeek
        ]
        
        fields.each { fieldName, fieldValue ->
            if (!fieldValue || fieldValue.isAllWhitespace()) {
                throw new CronParseException("Empty ${fieldName} field")
            }
        }
    }
    
    /**
     * Load crontab entries for the specified user (or current user).
     * 
     * On Windows, this function provides mock data for development purposes.
     * On Linux, it executes 'crontab -l' to get real data.
     * 
     * @param user Username to load crontab for (null for current user)
     * @return List of CronJob objects
     */
    static List<CronJob> loadUserCrontab(String user = null) {
        log.info("Loading crontab for user: ${user ?: 'current user'}")
        
        // Mock data for Windows development
        if (System.getProperty('os.name').toLowerCase().contains('windows')) {
            log.info('Running on Windows - using mock crontab data')
            return getMockCrontabData(user)
        }
        
        // Real implementation for Linux
        try {
            List<String> cmd = ['crontab', '-l']
            if (user) {
                cmd.addAll(['-u', user])
            }
            
            Process process = cmd.execute()
            String output = process.text
            int exitCode = process.waitFor()
            
            if (exitCode != 0) {
                String errorOutput = process.err.text
                if (errorOutput.toLowerCase().contains('no crontab for')) {
                    log.info("No crontab found for user ${user ?: 'current user'}")
                    return []
                }
                throw new IOException("crontab command failed: ${errorOutput}")
            }
            
            List<CronJob> cronJobs = []
            output.eachLine { String line, int lineNum ->
                try {
                    CronJob job = parseCronLine(line, 'user', user)
                    if (job) {
                        cronJobs.add(job)
                    }
                } catch (CronParseException e) {
                    log.warn("Failed to parse crontab line ${lineNum + 1}: ${e.message}")
                }
            }
            
            log.info("Loaded ${cronJobs.size()} cron jobs from user crontab")
            return cronJobs
            
        } catch (IOException e) {
            log.error("Error executing crontab command", e)
            throw e
        }
    }
    
    /**
     * Load crontab entries from a file.
     * 
     * @param filePath Path to the crontab file to load
     * @return List of CronJob objects parsed from the file
     * @throws FileNotFoundException If the file doesn't exist
     * @throws CronParseException If any cron lines are invalid
     */
    static List<CronJob> loadCrontabFromFile(String filePath) {
        File file = new File(filePath)
        
        if (!file.exists()) {
            throw new FileNotFoundException("Crontab file not found: ${filePath}")
        }
        
        if (!file.isFile()) {
            throw new IllegalArgumentException("Path is not a file: ${filePath}")
        }
        
        log.info("Loading crontab from file: ${filePath}")
        
        List<CronJob> jobs = []
        List<String> parseErrors = []
        
        try {
            file.eachLine { String line, int lineNumber ->
                try {
                    CronJob job = parseCronLine(line, 'system', null)
                    if (job) {
                        jobs.add(job)
                        log.debug("Parsed job from line ${lineNumber}: ${job.rawLine}")
                    }
                } catch (CronParseException e) {
                    String errorMsg = "Line ${lineNumber}: ${e.message}"
                    log.warn(errorMsg)
                    parseErrors.add(errorMsg)
                }
            }
        } catch (IOException e) {
            throw new CronParseException("Error reading crontab file: ${e.message}", e)
        }
        
        if (parseErrors) {
            log.warn("Encountered ${parseErrors.size()} parsing errors in ${filePath}")
            parseErrors.take(5).each { error ->
                log.warn("  ${error}")
            }
            if (parseErrors.size() > 5) {
                log.warn("  ... and ${parseErrors.size() - 5} more errors")
            }
        }
        
        log.info("Successfully loaded ${jobs.size()} cron jobs from ${filePath}")
        return jobs
    }
    
    /**
     * Provides mock crontab data for development/testing on Windows.
     */
    private static List<CronJob> getMockCrontabData(String user) {
        log.debug("Generating mock crontab data")
        
        String[] mockLines = [
            '0 0 * * * /usr/bin/backup-daily',
            '30 2 * * 1 /usr/bin/backup-weekly',
            '0 */6 * * * /usr/bin/check-updates',
            '@hourly /usr/bin/monitor-system',
            '15 3 1 * * /usr/bin/monthly-report'
        ]
        
        return mockLines.collect { line ->
            parseCronLine(line, 'user', user)
        }.findAll { it != null }
    }
}
