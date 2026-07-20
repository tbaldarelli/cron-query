package com.cronquery.service.parser;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cronquery.service.exception.InvalidQueryException;
import com.cronquery.service.parser.QueryCriteria.QueryType;
import com.cronquery.service.parser.QueryCriteria.TimeRange;

public class QueryParserImpl implements QueryParser {
    private static final Logger log = LoggerFactory.getLogger(QueryParserImpl.class);

    // Day name mappings (case-insensitive)
    // Support day names (Monday, Saturday, weekends, weekdays)
    private static final Map<String, DayOfWeek> DAY_NAMES = Map.ofEntries(
        Map.entry("sunday", DayOfWeek.SUNDAY),
        Map.entry("sun", DayOfWeek.SUNDAY),
        Map.entry("monday", DayOfWeek.MONDAY),
        Map.entry("mon", DayOfWeek.MONDAY),
        Map.entry("tuesday", DayOfWeek.TUESDAY),
        Map.entry("tue", DayOfWeek.TUESDAY),
        Map.entry("tues", DayOfWeek.TUESDAY),
        Map.entry("wednesday", DayOfWeek.WEDNESDAY),
        Map.entry("wed", DayOfWeek.WEDNESDAY),
        Map.entry("thursday", DayOfWeek.THURSDAY),
        Map.entry("thu", DayOfWeek.THURSDAY),
        Map.entry("thur", DayOfWeek.THURSDAY),
        Map.entry("thurs", DayOfWeek.THURSDAY),
        Map.entry("friday", DayOfWeek.FRIDAY),
        Map.entry("fri", DayOfWeek.FRIDAY),
        Map.entry("saturday", DayOfWeek.SATURDAY),
        Map.entry("sat", DayOfWeek.SATURDAY)
    );

    // Special day groupings
    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    );

    private static final Set<DayOfWeek> WEEKENDS = EnumSet.of(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    );

     /**
     * Parse a natural language query into structured criteria.
     * 
     * @param query Natural language query string
     * @return QueryCriteria object representing the parsed query
     * @throws InvalidQueryException If the query cannot be parsed
     */
    @Override
    public QueryCriteria parse(String query) throws InvalidQueryException {
        if (!(query instanceof String) || query.isBlank()) {
            throw new InvalidQueryException("Empty query");
        }
        
        String normalizedQuery = query.trim().toLowerCase();
        log.debug("Parsing query: '{}'", normalizedQuery );
        
        // Try to parse as combined query first (day + time)
        try {
            QueryCriteria combined = parseCombinedQuery(normalizedQuery);
            if (combined instanceof QueryCriteria) {
                log.debug("Parsed as combined query: {}", combined);
                return combined;
            }
        } catch (InvalidQueryException e) {
            if (e.getMessage().contains("Date conflict:")) {
                throw e;
            }
        }
        
        // Try to parse as day-based query
        try {
            QueryCriteria dayCriteria = parseDayQuery(normalizedQuery);
            if (dayCriteria instanceof QueryCriteria) {
                log.debug("Parsed as day query: {}", dayCriteria);
                return dayCriteria;
            }
        } catch (InvalidQueryException ignored) {
        }
        
        // Try to parse as time-based query
        try {
            QueryCriteria timeCriteria = parseTimeQuery(normalizedQuery);
            if (timeCriteria instanceof QueryCriteria) {
                log.debug("Parsed as time query: {}", timeCriteria);
                return timeCriteria;
            }
        } catch (InvalidQueryException ignored) {
        }
        
        // If we can't parse it, return unknown type
        log.warn("Could not parse query: '{}'", normalizedQuery);
        return new QueryCriteria(QueryType.UNKNOWN,
            null, null,
            null, null,
            false, normalizedQuery);
    }

        /**
     * Parse combined day + time queries like 'this Saturday after 10 AM'.
     */
    private static QueryCriteria parseCombinedQuery(String query) throws InvalidQueryException {
        String normalized = normalizeQuery(query);
        
        // Combined patterns
        List<Pattern> patterns = Arrays.asList(
            Pattern.compile("(?i)(this|next|coming|comming)\\s+(\\w+)\\s*,?\\s*(after|before|between)\\s+(.+)"),
            Pattern.compile("(?i)(\\w+day)\\s+(\\d{1,2}\\/\\d{1,2}\\/\\d{4})\\s*(after|before|between)\\s+(.+)"),
            Pattern.compile("(?i)(today|tomorrow|yesterday)\\s*(after|before|between)\\s+(.+)"),
            Pattern.compile("(?i)(\\w+day|weekends?|weekdays?)\\s*,?\\s*(after|before|between)\\s+(.+)"),
            Pattern.compile("(?i)(after|before|between)\\s+(.+)\\s+on\\s+(\\w+day|weekends?|weekdays?)")
        );
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(normalized);
            if (matcher.find()) {
                return parseCombinedMatch(normalized, matcher);
            }
        }
        
        return null;
    }
    
    /**
     * Parse the matched combined query pattern.
     */
    private static QueryCriteria parseCombinedMatch(String query, Matcher matcher) {
        // Extract day and time components
        String dayPart = null;
        String timeRelation = null;
        String timePart = null;
        
        int groupCount = matcher.groupCount();
        
        // Parse based on captured groups
        if (groupCount >= 4) {
            dayPart = matcher.group(2);
            timeRelation = matcher.group(3);
            timePart = matcher.group(4);
        } else if (groupCount == 3) {
            dayPart = matcher.group(1);
            timeRelation = matcher.group(2);
            timePart = matcher.group(3);
        }
        
        // Parse the day part
        QueryCriteria dayCriteria = parseDayQuery(dayPart);
        if (!(dayCriteria instanceof QueryCriteria)) {
            return null;
        }
        
        // Apply time constraints
        QueryCriteria combined = applyTimeConstraints(dayCriteria, timeRelation, timePart);
        combined = new QueryCriteria(QueryType.COMBINED,
            combined.daysOfWeek(), combined.exactTime(),
            combined.timeRange(), combined.specificDate(), combined.isRelativeDate(),
            query);
        
        return combined;
    }

     /**
     * Apply time constraints to existing criteria.
     */
    private static QueryCriteria applyTimeConstraints(QueryCriteria criteria, String timeRelation, String timePart) {
        QueryCriteria timeQuery = parseTimeQuery(timeRelation + " " + timePart);
        
        if (!(timeQuery instanceof QueryCriteria)) {
            return criteria;
        }
        
        return new QueryCriteria(criteria.type(),
            criteria.daysOfWeek(), 
            timeQuery.exactTime(), timeQuery.timeRange(),
            criteria.specificDate(), criteria.isRelativeDate(),
            criteria.rawQuery());
    }
    
    /**
     * Parse day-based queries like 'Saturday', 'weekdays', 'this Monday'.
     */
    private static QueryCriteria parseDayQuery(String query)  throws InvalidQueryException {
        String normalized = normalizeQuery(query);
        
        // Check for weekdays/weekends
        if(normalized.contains("weekdays") || normalized.contains("weekday") ) {
            return new QueryCriteria(QueryType.DAY_BASED, WEEKDAYS,
                null, null,
                null, false, query);
        }
        
        if(normalized.contains("weekends") || normalized.contains("weekend") ) {
            return new QueryCriteria(QueryType.DAY_BASED, WEEKENDS,
                null, null, null, false, query);
        }
        
        // Check for specific day names
        Set<DayOfWeek> foundDays = new HashSet<>();
        DAY_NAMES.entrySet().stream()
            .filter(entry -> normalized.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .forEach(foundDays::add);
        
        if (foundDays instanceof Set && !foundDays.isEmpty()) {
            boolean isSpecific = normalized.contains("this") ||
                normalized.contains("next") ||
                normalized.contains("coming");
            
            return new QueryCriteria(QueryType.DAY_BASED,
                foundDays, null, null, null, isSpecific, query);
        }
        
        // Check for today/tomorrow/yesterday
        if( normalized.contains("today") ||
            normalized.contains("tomorrow") ||
            normalized.contains("yesterday")) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime targetDate = now;
            
            if (normalized.contains("tomorrow")) {
                targetDate = now.plusDays(1);
            } else if (normalized.contains("yesterday")) {
                targetDate = now.minusDays(1);
            }
            
            return new QueryCriteria(QueryType.DAY_BASED, Set.of(targetDate.getDayOfWeek()),
                null, null, targetDate.toLocalDate(),
                false, query);
        }
        
        return null;
    }
    
    /**
     * Parse time-based queries like '8 AM', 'after 10 AM', 'between 9 AM and 5 PM'.
     */
    private static QueryCriteria parseTimeQuery(String query) throws InvalidQueryException {
        String normalized = normalizeQuery(query);
        
        // Try time ranges first
        QueryCriteria rangeCriteria = parseTimeRanges(normalized);
        if (rangeCriteria instanceof QueryCriteria) {
            return rangeCriteria;
        }
        
        // Try single time
        LocalTime time = parseSingleTime(normalized);
        if (time instanceof LocalTime) {
            return new QueryCriteria(QueryType.TIME_BASED,
                null, time, null, null, false, query);
            //     queryType: QueryType.TIME_BASED,
            //     rawQuery: query,
            //     timeHour: time.first,
            //     timeMinute: time.second
        }
        
        return null;
    }
    
    /**
     * Parse time range queries (after/before/between).
     */
    private static QueryCriteria parseTimeRanges(String query) {
        // Between X and Y
        Matcher betweenMatch = Pattern.compile("between\\s+(.+?)\\s+and\\s+(.+)").matcher(query);
        if (betweenMatch.find()) {
            LocalTime startTime = parseSingleTime(betweenMatch.group(1));
            LocalTime endTime = parseSingleTime(betweenMatch.group(2));
            
            if (startTime instanceof LocalTime && endTime instanceof LocalTime) {
                return new QueryCriteria(QueryType.TIME_BASED, null, null,
                    new TimeRange(startTime, endTime, TimeRange.RangeType.BETWEEN),
                    null, false, query);
            }
        }
        
        // After X
        Matcher afterMatch = Pattern.compile("after\\s+(.+)").matcher(query);
        if (afterMatch.find()) {
            LocalTime time = parseSingleTime(afterMatch.group(1));
            if (time instanceof LocalTime) {
                return new QueryCriteria(QueryType.TIME_BASED, null, null,
                    new TimeRange(time, null, TimeRange.RangeType.AFTER),
                    null, false, query);
            }
        }
        
        // Before X
        Matcher beforeMatch = Pattern.compile("before\\s+(.+)").matcher(query);
        if (beforeMatch.find()) {
            LocalTime time = parseSingleTime(beforeMatch.group(1));
            if (time instanceof LocalTime) {
                return new QueryCriteria(QueryType.TIME_BASED, null, null,
                    new TimeRange(null, time, TimeRange.RangeType.BEFORE),
                    null, false, query);
            }
        }
        
        return null;
    }
    
    /**
     * Parse a single time string like '8 AM', '3:30 PM', '14:00'.
     */
    private static LocalTime parseSingleTime(String timeStr) {
        String normalized = timeStr.trim().toLowerCase();
        
        // Try 12-hour format FIRST (8 AM, 3:30 PM)
        // This must come before 24-hour to avoid matching "3:45" in "3:45 PM"
        Matcher match12h = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)").matcher(normalized);
        if (match12h.find()) {
            int hour =  Integer.parseInt(match12h.group(1));
            int minute = (match12h.group(2) instanceof String && !match12h.group(2).isBlank()) ?
                Integer.parseInt(match12h.group(2)) : 0;
            String ampm = match12h.group(3);
            
            // Convert to 24-hour format
            if ("pm".equals(ampm) && hour != 12) {
                hour += 12;
            } else if ("am".equals(ampm) && hour == 12) {
                hour = 0;
            }
            
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return LocalTime.of(hour, minute);
            }
        }
        
        // Try 24-hour format (14:30, 9:00)
        Matcher match24h = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(normalized);
        if (match24h.find()) {
            int hour = Integer.parseInt(match24h.group(1));
            int minute = Integer.parseInt(match24h.group(2));
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return LocalTime.of(hour, minute);
            }
        }
        
        return null;
    }

    /**
     * Normalize query string (remove extra whitespace, etc.)
     */
    private static String normalizeQuery(String query) {
        return query.trim()
                    .replaceAll("\\s+", " ")
                    .replaceAll("[,;]", " ")
                    .toLowerCase();
    }

    @Override
    public String normalize(String query) {
        return normalizeQuery(query);
    }

}
