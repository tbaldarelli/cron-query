package com.cronquery.service.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal model for query criteria used in Groovy JAR integration.
 * Converts QueryRequest into a format suitable for the Groovy implementation.
 */
public class QueryCriteria {

    private List<String> days;
    private TimeRange timeRange;
    private String rawQuery;

    // Constructors
    public QueryCriteria() {
        this.days = new ArrayList<>();
    }

    public QueryCriteria(List<String> days, TimeRange timeRange, String rawQuery) {
        this.days = days != null ? days : new ArrayList<>();
        this.timeRange = timeRange;
        this.rawQuery = rawQuery;
    }

    // Getters and Setters
    public List<String> getDays() {
        return days;
    }

    public void setDays(List<String> days) {
        this.days = days != null ? days : new ArrayList<>();
    }

    public void addDay(String day) {
        if (day != null && !day.trim().isEmpty()) {
            this.days.add(day);
        }
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(TimeRange timeRange) {
        this.timeRange = timeRange;
    }

    public String getRawQuery() {
        return rawQuery;
    }

    public void setRawQuery(String rawQuery) {
        this.rawQuery = rawQuery;
    }

    /**
     * Check if this criteria has any filters.
     */
    public boolean hasFilters() {
        return (days != null && !days.isEmpty()) 
            || timeRange != null 
            || (rawQuery != null && !rawQuery.trim().isEmpty());
    }

    @Override
    public String toString() {
        return "QueryCriteria{" +
                "days=" + days +
                ", timeRange=" + timeRange +
                ", rawQuery='" + rawQuery + '\'' +
                '}';
    }

    /**
     * Time range representation for query criteria.
     */
    public static class TimeRange {
        private String startTime;
        private String endTime;

        public TimeRange() {
        }

        public TimeRange(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        @Override
        public String toString() {
            return startTime + "-" + endTime;
        }
    }
}
