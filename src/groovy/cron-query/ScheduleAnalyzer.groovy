package com.cronquery

import com.cronutils.model.Cron
import com.cronutils.model.definition.CronDefinition
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

import static com.cronutils.model.CronType.UNIX

/**
 * Exception raised when schedule analysis fails.
 */
class ScheduleAnalysisException extends Exception {
    ScheduleAnalysisException(String message) {
        super(message)
    }
    
    ScheduleAnalysisException(String message, Throwable cause) {
        super(message, cause)
    }
}

/**
 * Schedule Analyzer - Core logic for matching cron jobs against query criteria.
 * 
 * This class provides the core functionality to analyze cron job schedules
 * and determine which jobs match specific query criteria using cron-utils.
 */
@Slf4j
@CompileStatic
class ScheduleAnalyzer {
    
    private static final CronDefinition CRON_DEFINITION = CronDefinitionBuilder.instanceDefinitionFor(UNIX)
    private static final CronParser CRON_PARSER = new CronParser(CRON_DEFINITION)
    
    /**
     * Find cron jobs that match the given query criteria.
     * 
     * @param cronJobs List of cron jobs to analyze
     * @param criteria Query criteria to match against
     * @return List of matching cron jobs
     * @throws ScheduleAnalysisException If analysis fails
     */
    static List<CronJob> findMatchingJobs(List<CronJob> cronJobs, QueryCriteria criteria) {
        log.debug("Analyzing ${cronJobs.size()} jobs against criteria: ${criteria}")
        
        if (criteria.queryType == QueryType.UNKNOWN) {
            log.warn('Cannot analyze unknown query type')
            return []
        }
        
        List<CronJob> matchingJobs = []
        
        for (CronJob job : cronJobs) {
            try {
                if (matchesCriteria(job, criteria)) {
                    matchingJobs.add(job)
                    log.debug("Job matches: ${job.rawLine}")
                }
            } catch (Exception e) {
                log.warn("Error analyzing job '${job.rawLine}': ${e.message}")
                continue
            }
        }
        
        log.info("Found ${matchingJobs.size()} matching jobs out of ${cronJobs.size()}")
        return matchingJobs
    }
    
    /**
     * Check if a cron job matches the given criteria.
     */
    static boolean matchesCriteria(CronJob job, QueryCriteria criteria) {
        if (!job.isValid()) {
            log.debug("Skipping invalid job: ${job.rawLine}")
            return false
        }
        
        try {
            switch (criteria.queryType) {
                case QueryType.DAY_BASED:
                    if (criteria.isSpecificDate && criteria.specificDate) {
                        return runsOnSpecificDate(job, criteria.specificDate)
                    } else {
                        return runsOnDayOfWeek(job, criteria.daysOfWeek)
                    }
                    
                case QueryType.TIME_BASED:
                    if (criteria.isTimeAfter || criteria.isTimeBefore || criteria.isTimeBetween) {
                        return runsInTimeRange(job, criteria)
                    } else {
                        return runsAtTime(job, criteria.timeHour, criteria.timeMinute)
                    }
                    
                case QueryType.COMBINED:
                    boolean dayMatch = false
                    if (criteria.isSpecificDate && criteria.specificDate) {
                        dayMatch = runsOnSpecificDate(job, criteria.specificDate)
                    } else if (criteria.daysOfWeek) {
                        dayMatch = runsOnDayOfWeek(job, criteria.daysOfWeek)
                    }
                    
                    boolean timeMatch = false
                    if (criteria.isTimeAfter || criteria.isTimeBefore || criteria.isTimeBetween) {
                        timeMatch = runsInTimeRange(job, criteria)
                    } else if (criteria.timeHour != null) {
                        timeMatch = runsAtTime(job, criteria.timeHour, criteria.timeMinute)
                    }
                    
                    return dayMatch && timeMatch
                    
                default:
                    log.warn("Unknown query type: ${criteria.queryType}")
                    return false
            }
        } catch (Exception e) {
            throw new ScheduleAnalysisException("Failed to analyze job '${job.rawLine}': ${e.message}", e)
        }
    }
    
    /**
     * Check if a cron job runs on any of the specified days of the week.
     */
    static boolean runsOnDayOfWeek(CronJob job, Set<Integer> targetDays) {
        if (!targetDays) {
            return false
        }
        
        try {
            // Check if both day-of-month and day-of-week are specified
            boolean domSpecified = job.dayOfMonth != '*'
            boolean dowSpecified = job.dayOfWeek != '*'
            
            if (domSpecified && dowSpecified) {
                // Complex case: both specified - cron uses OR logic
                return checkComplexDayLogic(job, targetDays)
            } else if (dowSpecified) {
                // Simple case: only day-of-week specified
                return checkDayOfWeekField(job.dayOfWeek, targetDays)
            } else if (domSpecified) {
                // Day-of-month only with * for day-of-week means it runs every day
                // which includes the target days (e.g., weekdays)
                return true
            } else {
                // Both are * - runs every day
                return true
            }
        } catch (Exception e) {
            throw new ScheduleAnalysisException("Failed to check day-of-week for job '${job.rawLine}': ${e.message}", e)
        }
    }
    
    /**
     * Check if a cron job runs at the specified time.
     */
    static boolean runsAtTime(CronJob job, Integer targetHour, Integer targetMinute) {
        if (targetHour == null && targetMinute == null) {
            return true
        }
        
        try {
            boolean hourMatch = targetHour != null ? matchesTimeField(job.hour, targetHour) : true
            boolean minuteMatch = targetMinute != null ? matchesTimeField(job.minute, targetMinute) : true
            
            return hourMatch && minuteMatch
        } catch (Exception e) {
            throw new ScheduleAnalysisException("Failed to check time for job '${job.rawLine}': ${e.message}", e)
        }
    }
    
    /**
     * Check if a cron job runs on a specific date.
     */
    static boolean runsOnSpecificDate(CronJob job, LocalDateTime targetDate) {
        if (!job.isValid()) {
            return false
        }
        
        try {
            Cron cron = CRON_PARSER.parse(job.cronExpression)
            ExecutionTime executionTime = ExecutionTime.forCron(cron)
            
            // Check at start of target day
            ZonedDateTime startOfDay = targetDate.withHour(0).withMinute(0).withSecond(0)
                                                 .atZone(ZoneId.systemDefault())
            ZonedDateTime endOfDay = startOfDay.plusDays(1)
            
            // Check if there's any execution on the target date
            Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(startOfDay.minusMinutes(1))
            
            if (nextExecution.isPresent()) {
                ZonedDateTime next = nextExecution.get()
                return next.isAfter(startOfDay.minusSeconds(1)) && next.isBefore(endOfDay)
            }
            
            return false
        } catch (Exception e) {
            throw new ScheduleAnalysisException("Failed to check specific date for job '${job.rawLine}': ${e.message}", e)
        }
    }
    
    /**
     * Check if a cron job runs within the specified time range.
     */
    static boolean runsInTimeRange(CronJob job, QueryCriteria criteria) {
        if (!job.isValid()) {
            return false
        }
        
        try {
            // Parse hour and minute fields to get all possible run times
            Set<Integer> hourValues = parseCronField(job.hour, 0, 23)
            Set<Integer> minuteValues = parseCronField(job.minute, 0, 59)
            
            // Check each combination
            for (int hour : hourValues) {
                for (int minute : minuteValues) {
                    if (timeInRange(hour, minute, criteria)) {
                        return true
                    }
                }
            }
            
            return false
        } catch (Exception e) {
            throw new ScheduleAnalysisException("Failed to check time range for job '${job.rawLine}': ${e.message}", e)
        }
    }
    
    /**
     * Check if a specific time falls within the criteria's time range.
     */
    private static boolean timeInRange(int hour, int minute, QueryCriteria criteria) {
        int currentMinutes = hour * 60 + minute
        
        if (criteria.isTimeAfter && criteria.timeRangeStart) {
            int startMinutes = criteria.timeRangeStart.first * 60 + criteria.timeRangeStart.second
            return currentMinutes > startMinutes
        } else if (criteria.isTimeBefore && criteria.timeRangeEnd) {
            int endMinutes = criteria.timeRangeEnd.first * 60 + criteria.timeRangeEnd.second
            return currentMinutes < endMinutes
        } else if (criteria.isTimeBetween && criteria.timeRangeStart && criteria.timeRangeEnd) {
            int startMinutes = criteria.timeRangeStart.first * 60 + criteria.timeRangeStart.second
            int endMinutes = criteria.timeRangeEnd.first * 60 + criteria.timeRangeEnd.second
            
            if (startMinutes <= endMinutes) {
                // Normal range: 9 AM to 5 PM
                return startMinutes <= currentMinutes && currentMinutes <= endMinutes
            } else {
                // Overnight range: 10 PM to 6 AM
                return currentMinutes >= startMinutes || currentMinutes <= endMinutes
            }
        }
        
        return false
    }
    
    /**
     * Handle complex day logic when both day-of-month and day-of-week are specified.
     */
    private static boolean checkComplexDayLogic(CronJob job, Set<Integer> targetDays) {
        // Check if day-of-week field matches
        if (checkDayOfWeekField(job.dayOfWeek, targetDays)) {
            return true
        }
        
        // For simplicity, we'll accept DOM+DOW combinations as potential matches
        // A full implementation would need to sample future dates
        return false
    }
    
    /**
     * Check if a day-of-week field matches any target days.
     */
    private static boolean checkDayOfWeekField(String dowField, Set<Integer> targetDays) {
        if (dowField == '*') {
            return true
        }
        
        Set<Integer> matchingDays = parseCronField(dowField, 0, 6)
        
        // Convert Sunday from 7 to 0 if present
        matchingDays = matchingDays.collect { it == 7 ? 0 : it } as Set<Integer>
        
        // Check if any matching days overlap with target days
        return !matchingDays.intersect(targetDays).isEmpty()
    }
    
    /**
     * Check if a cron time field matches a target value.
     */
    private static boolean matchesTimeField(String cronField, int targetValue) {
        if (cronField == '*') {
            return true
        }
        
        // Determine range based on value
        int minVal = 0
        int maxVal = targetValue <= 23 ? 23 : 59
        
        Set<Integer> matchingValues = parseCronField(cronField, minVal, maxVal)
        return targetValue in matchingValues
    }
    
    /**
     * Parse a cron field and return set of matching values.
     * 
     * Handles patterns like: 1, 1-5, 1,3,5, *\/2, 8-12/2, etc.
     */
    static Set<Integer> parseCronField(String field, int minVal, int maxVal) {
        Set<Integer> values = [] as Set<Integer>
        
        // Split by comma for multiple values
        String[] parts = field.split(',')
        
        for (String part : parts) {
            part = part.trim()
            
            if (part.contains('/')) {
                // Handle step values like */2, 1-5/2
                String[] stepParts = part.split('/', 2)
                String rangePart = stepParts[0]
                int step = stepParts[1] as int
                
                if (rangePart == '*') {
                    // */step - every step values in full range
                    for (int i = minVal; i <= maxVal; i += step) {
                        values.add(i)
                    }
                } else if (rangePart.contains('-')) {
                    // start-end/step
                    String[] range = rangePart.split('-', 2)
                    int start = range[0] as int
                    int end = range[1] as int
                    for (int i = start; i <= end; i += step) {
                        values.add(i)
                    }
                } else {
                    // single_value/step
                    int start = rangePart as int
                    for (int i = start; i <= maxVal; i += step) {
                        values.add(i)
                    }
                }
            } else if (part.contains('-')) {
                // Handle ranges like 1-5
                String[] range = part.split('-', 2)
                int start = range[0] as int
                int end = range[1] as int
                for (int i = start; i <= end; i++) {
                    values.add(i)
                }
            } else {
                // Single value or wildcard
                if (part == '*') {
                    for (int i = minVal; i <= maxVal; i++) {
                        values.add(i)
                    }
                } else {
                    values.add(part as int)
                }
            }
        }
        
        // Handle Sunday conversion for day-of-week
        if (minVal == 0 && maxVal == 6) {
            values = values.collect { it == 7 ? 0 : it } as Set<Integer>
        }
        
        // Filter to valid range
        return values.findAll { it >= minVal && it <= maxVal } as Set<Integer>
    }
    
    /**
     * Get the next scheduled run times for a cron job.
     */
    static List<ZonedDateTime> getNextRuns(CronJob job, int count = 5, ZonedDateTime startTime = null) {
        if (!job.isValid()) {
            throw new ScheduleAnalysisException("Invalid cron expression: ${job.cronExpression}")
        }
        
        try {
            if (startTime == null) {
                startTime = ZonedDateTime.now()
            }
            
            Cron cron = CRON_PARSER.parse(job.cronExpression)
            ExecutionTime executionTime = ExecutionTime.forCron(cron)
            
            List<ZonedDateTime> nextRuns = []
            Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(startTime)
            
            while (nextRuns.size() < count && nextExecution.isPresent()) {
                ZonedDateTime next = nextExecution.get()
                nextRuns.add(next)
                nextExecution = executionTime.nextExecution(next)
            }
            
            return nextRuns
        } catch (Exception e) {
            throw new ScheduleAnalysisException("Failed to calculate next runs for job '${job.rawLine}': ${e.message}", e)
        }
    }
}
