package com.cronquery

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.util.logging.Slf4j

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Types of queries supported by the parser.
 */
@CompileStatic
enum QueryType {
    DAY_BASED,
    TIME_BASED,
    COMBINED,
    UNKNOWN
}

/**
 * Represents parsed query criteria for filtering cron jobs.
 */
@Immutable
@CompileStatic
class QueryCriteria {
    QueryType queryType
    String rawQuery
    Set<Integer> daysOfWeek
    Integer timeHour
    Integer timeMinute
    boolean isSpecificDate = false
    boolean weekdaysOnly = false
    boolean weekendsOnly = false
    LocalDateTime specificDate
    Tuple2<Integer, Integer> timeRangeStart  // (hour, minute)
    Tuple2<Integer, Integer> timeRangeEnd    // (hour, minute)
    boolean isTimeAfter = false
    boolean isTimeBefore = false
    boolean isTimeBetween = false
}

/**
 * Exception raised when parsing a query fails.
 */
class QueryParseException extends Exception {
    QueryParseException(String message) {
        super(message)
    }
    
    QueryParseException(String message, Throwable cause) {
        super(message, cause)
    }
}

/**
 * Query Parser - Handles natural language query parsing for cron schedules.
 * 
 * This class converts natural language queries into structured criteria
 * that can be used to filter and analyze cron jobs.
 */
@Slf4j
@CompileStatic
class QueryParser {
    
    // Day name mappings (case-insensitive)
    private static final Map<String, Integer> DAY_NAMES = [
        'sunday': 0, 'sun': 0,
        'monday': 1, 'mon': 1,
        'tuesday': 2, 'tue': 2, 'tues': 2,
        'wednesday': 3, 'wed': 3,
        'thursday': 4, 'thu': 4, 'thur': 4, 'thurs': 4,
        'friday': 5, 'fri': 5,
        'saturday': 6, 'sat': 6
    ].asImmutable()
    
    // Special day groupings
    private static final Set<Integer> WEEKDAYS = [1, 2, 3, 4, 5] as Set<Integer>
    private static final Set<Integer> WEEKENDS = [0, 6] as Set<Integer>
    
    /**
     * Parse a natural language query into structured criteria.
     * 
     * @param query Natural language query string
     * @return QueryCriteria object representing the parsed query
     * @throws QueryParseException If the query cannot be parsed
     */
    static QueryCriteria parseQuery(String query) {
        if (!query?.trim()) {
            throw new QueryParseException('Empty query')
        }
        
        String normalizedQuery = query.trim().toLowerCase()
        log.debug("Parsing query: '${normalizedQuery}'")
        
        // Try to parse as combined query first (day + time)
        try {
            QueryCriteria combined = parseCombinedQuery(normalizedQuery)
            if (combined) {
                log.debug("Parsed as combined query: ${combined}")
                return combined
            }
        } catch (QueryParseException e) {
            if (e.message.contains('Date conflict:')) {
                throw e
            }
        }
        
        // Try to parse as day-based query
        try {
            QueryCriteria dayCriteria = parseDayQuery(normalizedQuery)
            if (dayCriteria) {
                log.debug("Parsed as day query: ${dayCriteria}")
                return dayCriteria
            }
        } catch (QueryParseException ignored) {
        }
        
        // Try to parse as time-based query
        try {
            QueryCriteria timeCriteria = parseTimeQuery(normalizedQuery)
            if (timeCriteria) {
                log.debug("Parsed as time query: ${timeCriteria}")
                return timeCriteria
            }
        } catch (QueryParseException ignored) {
        }
        
        // If we can't parse it, return unknown type
        log.warn("Could not parse query: '${normalizedQuery}'")
        return new QueryCriteria(
            queryType: QueryType.UNKNOWN,
            rawQuery: normalizedQuery
        )
    }
    
    /**
     * Parse combined day + time queries like 'this Saturday after 10 AM'.
     */
    private static QueryCriteria parseCombinedQuery(String query) {
        String normalized = normalizeQuery(query)
        
        // Combined patterns
        List<Pattern> patterns = [
            ~/(?i)(this|next|coming|comming)\s+(\w+)\s*,?\s*(after|before|between)\s+(.+)/,
            ~/(?i)(\w+day)\s+(\d{1,2}\/\d{1,2}\/\d{4})\s*(after|before|between)\s+(.+)/,
            ~/(?i)(today|tomorrow|yesterday)\s*(after|before|between)\s+(.+)/,
            ~/(?i)(\w+day|weekends?|weekdays?)\s*,?\s*(after|before|between)\s+(.+)/,
            ~/(?i)(after|before|between)\s+(.+)\s+on\s+(\w+day|weekends?|weekdays?)/
        ]
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(normalized)
            if (matcher.find()) {
                return parseCombinedMatch(normalized, matcher)
            }
        }
        
        return null
    }
    
    /**
     * Parse the matched combined query pattern.
     */
    private static QueryCriteria parseCombinedMatch(String query, Matcher matcher) {
        // Extract day and time components
        String dayPart = null
        String timeRelation = null
        String timePart = null
        
        int groupCount = matcher.groupCount()
        
        // Parse based on captured groups
        if (groupCount >= 4) {
            dayPart = matcher.group(2)
            timeRelation = matcher.group(3)
            timePart = matcher.group(4)
        } else if (groupCount == 3) {
            dayPart = matcher.group(1)
            timeRelation = matcher.group(2)
            timePart = matcher.group(3)
        }
        
        // Parse the day part
        QueryCriteria dayCriteria = parseDayQuery(dayPart)
        if (!dayCriteria) {
            return null
        }
        
        // Apply time constraints
        QueryCriteria combined = applyTimeConstraints(dayCriteria, timeRelation, timePart)
        combined = new QueryCriteria(
            queryType: QueryType.COMBINED,
            rawQuery: query,
            daysOfWeek: combined.daysOfWeek,
            timeHour: combined.timeHour,
            timeMinute: combined.timeMinute,
            isSpecificDate: combined.isSpecificDate,
            weekdaysOnly: combined.weekdaysOnly,
            weekendsOnly: combined.weekendsOnly,
            specificDate: combined.specificDate,
            timeRangeStart: combined.timeRangeStart,
            timeRangeEnd: combined.timeRangeEnd,
            isTimeAfter: combined.isTimeAfter,
            isTimeBefore: combined.isTimeBefore,
            isTimeBetween: combined.isTimeBetween
        )
        
        return combined
    }
    
    /**
     * Parse day-based queries like 'Saturday', 'weekdays', 'this Monday'.
     */
    private static QueryCriteria parseDayQuery(String query) {
        String normalized = normalizeQuery(query)
        
        // Check for weekdays/weekends
        if (normalized =~ /weekdays?/) {
            return new QueryCriteria(
                queryType: QueryType.DAY_BASED,
                rawQuery: query,
                daysOfWeek: WEEKDAYS,
                weekdaysOnly: true
            )
        }
        
        if (normalized =~ /weekends?/) {
            return new QueryCriteria(
                queryType: QueryType.DAY_BASED,
                rawQuery: query,
                daysOfWeek: WEEKENDS,
                weekendsOnly: true
            )
        }
        
        // Check for specific day names
        Set<Integer> foundDays = [] as Set<Integer>
        DAY_NAMES.each { name, dayNum ->
            if (normalized.contains(name)) {
                foundDays.add(dayNum)
            }
        }
        
        if (foundDays) {
            boolean isSpecific = normalized =~ /\b(this|next|coming)\b/
            return new QueryCriteria(
                queryType: QueryType.DAY_BASED,
                rawQuery: query,
                daysOfWeek: foundDays,
                isSpecificDate: isSpecific
            )
        }
        
        // Check for today/tomorrow/yesterday
        if (normalized =~ /\b(today|tomorrow|yesterday)\b/) {
            LocalDateTime now = LocalDateTime.now()
            LocalDateTime targetDate = now
            
            if (normalized.contains('tomorrow')) {
                targetDate = now.plusDays(1)
            } else if (normalized.contains('yesterday')) {
                targetDate = now.minusDays(1)
            }
            
            int dayNum = targetDate.dayOfWeek.value % 7 // Convert to 0=Sun format
            
            return new QueryCriteria(
                queryType: QueryType.DAY_BASED,
                rawQuery: query,
                daysOfWeek: [dayNum] as Set<Integer>,
                isSpecificDate: true,
                specificDate: targetDate
            )
        }
        
        return null
    }
    
    /**
     * Parse time-based queries like '8 AM', 'after 10 AM', 'between 9 AM and 5 PM'.
     */
    private static QueryCriteria parseTimeQuery(String query) {
        String normalized = normalizeQuery(query)
        
        // Try time ranges first
        QueryCriteria rangeCriteria = parseTimeRanges(normalized)
        if (rangeCriteria) {
            return rangeCriteria
        }
        
        // Try single time
        Tuple2<Integer, Integer> time = parseSingleTime(normalized)
        if (time) {
            return new QueryCriteria(
                queryType: QueryType.TIME_BASED,
                rawQuery: query,
                timeHour: time.first,
                timeMinute: time.second
            )
        }
        
        return null
    }
    
    /**
     * Parse time range queries (after/before/between).
     */
    private static QueryCriteria parseTimeRanges(String query) {
        // Between X and Y
        Matcher betweenMatch = (query =~ /between\s+(.+?)\s+and\s+(.+)/)
        if (betweenMatch) {
            Tuple2<Integer, Integer> startTime = parseSingleTime(betweenMatch.group(1))
            Tuple2<Integer, Integer> endTime = parseSingleTime(betweenMatch.group(2))
            
            if (startTime && endTime) {
                return new QueryCriteria(
                    queryType: QueryType.TIME_BASED,
                    rawQuery: query,
                    timeRangeStart: startTime,
                    timeRangeEnd: endTime,
                    isTimeBetween: true
                )
            }
        }
        
        // After X
        Matcher afterMatch = (query =~ /after\s+(.+)/)
        if (afterMatch) {
            Tuple2<Integer, Integer> time = parseSingleTime(afterMatch.group(1))
            if (time) {
                return new QueryCriteria(
                    queryType: QueryType.TIME_BASED,
                    rawQuery: query,
                    timeRangeStart: time,
                    isTimeAfter: true
                )
            }
        }
        
        // Before X
        Matcher beforeMatch = (query =~ /before\s+(.+)/)
        if (beforeMatch) {
            Tuple2<Integer, Integer> time = parseSingleTime(beforeMatch.group(1))
            if (time) {
                return new QueryCriteria(
                    queryType: QueryType.TIME_BASED,
                    rawQuery: query,
                    timeRangeEnd: time,
                    isTimeBefore: true
                )
            }
        }
        
        return null
    }
    
    /**
     * Parse a single time string like '8 AM', '3:30 PM', '14:00'.
     */
    private static Tuple2<Integer, Integer> parseSingleTime(String timeStr) {
        String normalized = timeStr.trim().toLowerCase()
        
        // Try 24-hour format (14:30, 9:00)
        Matcher match24h = (normalized =~ /(\d{1,2}):(\d{2})/)
        if (match24h) {
            int hour = match24h.group(1) as int
            int minute = match24h.group(2) as int
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return new Tuple2(hour, minute)
            }
        }
        
        // Try 12-hour format (8 AM, 3:30 PM)
        Matcher match12h = (normalized =~ /(\d{1,2})(?::(\d{2}))?\s*(am|pm)/)
        if (match12h) {
            int hour = match12h.group(1) as int
            int minute = match12h.group(2) ? (match12h.group(2) as int) : 0
            String ampm = match12h.group(3)
            
            // Convert to 24-hour format
            if (ampm == 'pm' && hour != 12) {
                hour += 12
            } else if (ampm == 'am' && hour == 12) {
                hour = 0
            }
            
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return new Tuple2(hour, minute)
            }
        }
        
        return null
    }
    
    /**
     * Apply time constraints to existing criteria.
     */
    private static QueryCriteria applyTimeConstraints(QueryCriteria criteria, String timeRelation, String timePart) {
        QueryCriteria timeQuery = parseTimeQuery("${timeRelation} ${timePart}")
        
        if (!timeQuery) {
            return criteria
        }
        
        return new QueryCriteria(
            queryType: criteria.queryType,
            rawQuery: criteria.rawQuery,
            daysOfWeek: criteria.daysOfWeek,
            timeHour: timeQuery.timeHour,
            timeMinute: timeQuery.timeMinute,
            isSpecificDate: criteria.isSpecificDate,
            weekdaysOnly: criteria.weekdaysOnly,
            weekendsOnly: criteria.weekendsOnly,
            specificDate: criteria.specificDate,
            timeRangeStart: timeQuery.timeRangeStart,
            timeRangeEnd: timeQuery.timeRangeEnd,
            isTimeAfter: timeQuery.isTimeAfter,
            isTimeBefore: timeQuery.isTimeBefore,
            isTimeBetween: timeQuery.isTimeBetween
        )
    }
    
    /**
     * Normalize query string (remove extra whitespace, etc.)
     */
    private static String normalizeQuery(String query) {
        return query.trim()
                    .replaceAll(/\s+/, ' ')
                    .replaceAll(/[,;]/, ' ')
                    .toLowerCase()
    }
    
    /**
     * Format criteria into human-readable description.
     */
    static String formatCriteriaDescription(QueryCriteria criteria) {
        if (!criteria || criteria.queryType == QueryType.UNKNOWN) {
            return 'Unknown query'
        }
        
        StringBuilder desc = new StringBuilder()
        
        // Add day info
        if (criteria.daysOfWeek) {
            if (criteria.weekdaysOnly) {
                desc.append('weekdays')
            } else if (criteria.weekendsOnly) {
                desc.append('weekends')
            } else {
                List<String> dayNames = criteria.daysOfWeek.collect { dayNum ->
                    String name = DAY_NAMES.find { it.value == dayNum }?.key
                    return (name ?: "day${dayNum}") as String
                } as List<String>
                desc.append(dayNames.join(', '))
            }
        }
        
        // Add time info
        if (criteria.isTimeBetween && criteria.timeRangeStart && criteria.timeRangeEnd) {
            if (desc) desc.append(' ')
            desc.append("between ${formatTime(criteria.timeRangeStart)} and ${formatTime(criteria.timeRangeEnd)}")
        } else if (criteria.isTimeAfter && criteria.timeRangeStart) {
            if (desc) desc.append(' ')
            desc.append("after ${formatTime(criteria.timeRangeStart)}")
        } else if (criteria.isTimeBefore && criteria.timeRangeEnd) {
            if (desc) desc.append(' ')
            desc.append("before ${formatTime(criteria.timeRangeEnd)}")
        } else if (criteria.timeHour != null) {
            if (desc) desc.append(' at ')
            desc.append("${formatTime(new Tuple2(criteria.timeHour, criteria.timeMinute ?: 0))}")
        }
        
        return desc.toString() ?: 'any time'
    }
    
    /**
     * Format time tuple as string.
     */
    private static String formatTime(Tuple2<Integer, Integer> time) {
        int hour = time.first
        int minute = time.second
        
        String ampm = hour >= 12 ? 'PM' : 'AM'
        int displayHour = hour % 12
        if (displayHour == 0) displayHour = 12
        
        if (minute == 0) {
            return "${displayHour} ${ampm}"
        } else {
            return String.format('%d:%02d %s', displayHour, minute, ampm)
        }
    }
}
