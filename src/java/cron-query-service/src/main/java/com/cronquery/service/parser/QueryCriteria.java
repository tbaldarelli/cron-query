package com.cronquery.service.parser;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Structured criteria parsed from a natural language query.
 * Uses Java 21 record for immutability.
 *
 * @param type        the classification of the query
 * @param daysOfWeek  target days (null if not day-based)
 * @param exactTime   exact time target (null if not specified)
 * @param timeRange   time range target (null if not specified)
 * @param specificDate specific calendar date (null if not date-based)
 * @param isRelativeDate whether the date came from a relative reference (this/next)
 * @param rawQuery    the original query string
 */
public record QueryCriteria(
    QueryType type,
    Set<DayOfWeek> daysOfWeek,
    LocalTime exactTime,
    TimeRange timeRange,
    LocalDate specificDate,
    boolean isRelativeDate,
    String rawQuery
) {

    /** Classification of the parsed query. */
    public enum QueryType {
        DAY_BASED,
        TIME_BASED,
        COMBINED,
        UNKNOWN
    }

    /**
     * A time range constraint (after, before, or between).
     *
     * @param start start of the range (null for BEFORE)
     * @param end   end of the range (null for AFTER)
     * @param type  the kind of range
     */
    public record TimeRange(
        LocalTime start,
        LocalTime end,
        RangeType type
    ) {
        /** Kind of time range. */
        public enum RangeType {
            AFTER,
            BEFORE,
            BETWEEN
        }
    }

    /** True when the criteria include day-of-week constraints. */
    public boolean hasDayCriteria() {
        return daysOfWeek != null && !daysOfWeek.isEmpty();
    }

    /** True when the criteria include a time or time-range constraint. */
    public boolean hasTimeCriteria() {
        return exactTime != null || timeRange != null;
    }

    /** True when the criteria target a specific calendar date. */
    public boolean isSpecificDate() {
        return specificDate != null;
    }
}
