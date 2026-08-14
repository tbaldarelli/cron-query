package com.cronquery.service.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

import com.cronquery.service.exception.InvalidQueryException;

public class QueryParserImplTest {
    private QueryParser queryParser;
    private LocalDate today;
    
    @BeforeEach
    void setUp() {
        queryParser = new QueryParserImpl();
        today = java.time.LocalDate.now();
    }

    // - Test day-only queries (Saturday, weekends, weekdays)
    @ParameterizedTest
    @CsvSource({
        "saturday, SATURDAY",
        "Monday, MONDAY",
        "Tue, TUESDAY"
    })
    @DisplayName("Should parse day-only queries")
    void testParseDayOnlyQueries(String query, DayOfWeek expectedDay) {
        QueryCriteria criteria = queryParser.parse(query);
        assertEquals(QueryCriteria.QueryType.DAY_BASED, criteria.type());
        assertTrue(criteria.daysOfWeek().contains(expectedDay));
    }

    @ParameterizedTest
    @CsvSource({  
        "weekends",
        "weekend",
        "Weekend"
    })
    @DisplayName("Should parse weekend queries")
    void testParseWeekendQuery(String query)
    {
        QueryCriteria criteria = queryParser.parse(query);
        assertEquals(QueryCriteria.QueryType.DAY_BASED, criteria.type());
        assertTrue(criteria.daysOfWeek().containsAll(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)));
    }

    @ParameterizedTest
    @CsvSource({  
        "weekdays",
        "weekday",
        "Weekday"
    })
    @DisplayName("Should parse weekday queries")
    void testParseWeekdayQuery(String query)
    {
        QueryCriteria criteria = queryParser.parse(query);
        assertEquals(QueryCriteria.QueryType.DAY_BASED, criteria.type());
        assertTrue(criteria.daysOfWeek().containsAll(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)));
    }

    // - Test time-only queries (8 AM, noon, 20:30)
    @ParameterizedTest
    @CsvSource({  
        "8 AM, 8, 0",
        "noon, 12, 0",
        "20:30, 20, 30"
    })
    @DisplayName("Should parse time only queries")
    void testParseTimeOnlyQuery(String query, int hour, int minute)
    {
        QueryCriteria criteria = queryParser.parse(query);
        assertEquals(QueryCriteria.QueryType.TIME_BASED, criteria.type(),
            "Query '" + query + "' invalid type.");
        assertTrue(LocalTime.of(hour, minute).equals(criteria.exactTime()));
    }

    // - Test time range queries (after, before, between)
    @Test
    @DisplayName("Should parse times after 8 am queries")
    void testParseTimeRangeAfter8AmQuery()
    {
        QueryCriteria criteria = queryParser.parse("after 8 am");
        assertEquals(QueryCriteria.QueryType.TIME_BASED, criteria.type());
        assertEquals(QueryCriteria.TimeRange.RangeType.AFTER, criteria.timeRange().type());
        assertEquals(LocalTime.of(8, 0), criteria.timeRange().start());
        assertNull(criteria.timeRange().end());
    }

    @Test
    @DisplayName("Should parse times before 3 pm queries")
    void testParseTimeRangeBefore3PmQuery()
    {
        QueryCriteria criteria = queryParser.parse("before 3pm");
        assertEquals(QueryCriteria.QueryType.TIME_BASED, criteria.type());
        assertEquals(QueryCriteria.TimeRange.RangeType.BEFORE, criteria.timeRange().type());
        assertNull(criteria.timeRange().start());
        assertEquals(LocalTime.of(15, 0), criteria.timeRange().end());
    }
    
    @Test
    @DisplayName("Should parse times between 1300 and 1530 queries")
    void testParseTimeRangeBetween1300And1530Query()
    {
        QueryCriteria criteria = queryParser.parse("between 1300 and 1530");
        assertEquals(QueryCriteria.QueryType.TIME_BASED, criteria.type());
        assertEquals(QueryCriteria.TimeRange.RangeType.BETWEEN, criteria.timeRange().type());
        assertEquals(LocalTime.of(13, 0), criteria.timeRange().start());
        assertEquals(LocalTime.of(15, 30), criteria.timeRange().end());
    }

    // - Test combined queries (day + time, day + range)
    @Test
    @DisplayName("Should parse day of Monday at 9:30am")
    void testParseMondayAt930AmQuery()
    {
        QueryCriteria criteria = queryParser.parse("Monday at 9:30 am");
        assertEquals(QueryCriteria.QueryType.COMBINED, criteria.type());
        assertEquals(Set.of(DayOfWeek.MONDAY), criteria.daysOfWeek());
        assertEquals(LocalTime.of(9, 30), criteria.exactTime());
        assertNull(criteria.timeRange());
    }

    @Test
    @DisplayName("Should parse day of Tuesday between 16:00 and 18:30")
    void testParseTuesday1600To1830Query()
    {
        QueryCriteria criteria = queryParser.parse("Tuesday between 1600 and 1830");
        assertEquals(QueryCriteria.QueryType.COMBINED, criteria.type());
        assertEquals(QueryCriteria.TimeRange.RangeType.BETWEEN, criteria.timeRange().type());
        assertEquals(Set.of(DayOfWeek.TUESDAY), criteria.daysOfWeek());
        assertEquals(LocalTime.of(16, 0), criteria.timeRange().start());
        assertEquals(LocalTime.of(18, 30), criteria.timeRange().end());
    }

    @Test
    @DisplayName("Should parse day of Tuesday between 4 pm and 6:30 pm")
    void testParseTuesday4PmTo630PmQuery()
    {
        QueryCriteria criteria = queryParser.parse("Tuesday between 4pm and 6:30pm");
        assertEquals(QueryCriteria.QueryType.COMBINED, criteria.type());
        assertEquals(QueryCriteria.TimeRange.RangeType.BETWEEN, criteria.timeRange().type());
        assertEquals(Set.of(DayOfWeek.TUESDAY), criteria.daysOfWeek());
        assertEquals(LocalTime.of(16, 0), criteria.timeRange().start());
        assertEquals(LocalTime.of(18, 30), criteria.timeRange().end());
    }

    // - Test relative dates (today, yesterday, this Saturday)
    @Test
    @DisplayName("Should parse today")
    void testParseTodayQuery()
    {
        QueryCriteria criteria = queryParser.parse("Today");
        assertEquals(QueryCriteria.QueryType.DAY_BASED, criteria.type());
        assertEquals(today, criteria.specificDate());
    }

    @Test
    @DisplayName("Should parse tomorrow")
    void testParseTomorrowQuery()
    {
        QueryCriteria criteria = queryParser.parse("tomorrow");
        assertEquals(QueryCriteria.QueryType.DAY_BASED, criteria.type());
        assertEquals(today.plusDays(1), criteria.specificDate());
    }

    @Test
    @DisplayName("Should parse yesterday")
    void testParseYesterdayQuery()
    {
        QueryCriteria criteria = queryParser.parse("yesterday");
        assertEquals(QueryCriteria.QueryType.DAY_BASED, criteria.type());
        assertEquals(today.minusDays(1), criteria.specificDate());
    }

    @Test
    @DisplayName("Should parse this Saturday")
    void testParseThisSaturdayQuery()
    {
        QueryCriteria criteria = queryParser.parse("this Saturday");
        assertEquals(QueryCriteria.QueryType.DAY_BASED, criteria.type());
        assertEquals(criteria.specificDate().getDayOfWeek(), DayOfWeek.SATURDAY);
        LocalDate thisSaturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        assertEquals(thisSaturday, criteria.specificDate());
    }

    // - Test explicit dates (9/18/2025, 2025-09-18)
    @ParameterizedTest
    @CsvSource({  
        "9/18/2025, 2025, 9, 18",
        "09/25/2025, 2025, 9, 25",
        "2025-9-1, 2025, 9, 1",
        "2025-08-05, 2025, 8, 5"
    })
    @DisplayName("Should parse exact date queries")
    void testParseExactDateQuery(String query, int year, int month, int dayOfMonth)
    {
        QueryCriteria criteria = queryParser.parse(query);
        assertEquals(QueryCriteria.QueryType.DAY_BASED, criteria.type(),
            "Query '" + query + "' should be day based.");
        assertEquals(LocalDate.of(year, month, dayOfMonth), criteria.specificDate());
    }

    // - Test day-date combinations and conflicts
    @Test
    @DisplayName("Should throw exception when day name conflicts with explicit date")
    void testDayDateConflict() {
        InvalidQueryException exception = assertThrows(InvalidQueryException.class,
            () -> queryParser.parse("Saturday 9/18/2025"));
        assertTrue(exception.getMessage().contains("Thursday"));
        assertTrue(exception.getMessage().contains("Saturday"));
    }

    @Test
    @DisplayName("Should not throw exception when day name does not conflict with explicit date")
    void testDayDateNoConflict() {
        QueryCriteria criteria = queryParser.parse("Thursday 9/18/2025");
        assertEquals(QueryCriteria.QueryType.DAY_BASED, criteria.type());
        assertEquals(LocalDate.of(2025, 9, 18), criteria.specificDate());
        assertTrue(criteria.daysOfWeek().contains(DayOfWeek.THURSDAY));
    }

    @Test
    @DisplayName("Should throw exception when day name conflicts with explicit date")
    void testDayDateTimeConflict() {
        InvalidQueryException exception = assertThrows(InvalidQueryException.class,
            () -> queryParser.parse("Saturday 9/18/2025 after 10 AM"));
        // System.out.println("Exception message: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("Thursday"));
        assertTrue(exception.getMessage().contains("Saturday"));
    }

    @Test
    @DisplayName("Should not throw exception when day name does not conflict with explicit date")
    void testDayDateTimeNoConflict() {
        QueryCriteria criteria = queryParser.parse("Thursday 9/18/2025 after 10 am");
        assertEquals(QueryCriteria.QueryType.COMBINED, criteria.type());
        assertEquals(LocalDate.of(2025, 9, 18), criteria.specificDate());
        assertTrue(criteria.daysOfWeek().contains(DayOfWeek.THURSDAY));
    }

    // - Test query normalization (prefix removal)
    @ParameterizedTest
    @CsvSource({  
        "which jobs run on Saturday, saturday",
        "what jobs run at 8 AM, at 8 am",
        "show me jobs that run on weekdays, weekdays",
        "jobs that run on Friday, friday"
    })
    @DisplayName("Should remove common prefixes from query")
    void testRemoveCommonPrefix(String query, String expected)
    {
        String normalized = queryParser.normalize(query);
        assertEquals(expected, normalized);
    }    

    @ParameterizedTest
    @CsvSource({  
        "on Saturday, saturday",
        "at 8 AM, at 8 am",
        "in the morning, morning",
        "during weekdays, weekdays"
    })
    @DisplayName("Should remove prepositions from query")
    void testRemovePrepositions(String query, String expected)
    {
        String normalized = queryParser.normalize(query);
        assertEquals(expected, normalized);
    }    
        
    @ParameterizedTest
    @CsvSource({  
        "Saturday morning, saturday morning",   // 'morning' is preserved
        "next Friday, next friday",             // 'next' is preserved
        "8:30 PM, 8:30 pm",                      // Time format preserved
        "Show me jobs next Tuesday, next tuesday"
    })
    @DisplayName("Should should preserve meaningful words from query")
    void testPreserveMeaningfulWords(String query, String expected)
    {
        String normalized = queryParser.normalize(query);
        assertEquals(expected, normalized);
    }    
            
    // - Test invalid queries and error messages
    @Test
    @DisplayName("Should throw exception for invalid query")
    void testParseInvalidQuery() {
        String invalidQuery = "";
        InvalidQueryException exception = assertThrows(InvalidQueryException.class,
            () -> queryParser.parse(invalidQuery));
        assertEquals(invalidQuery, exception.getInvalidQuery());
    }

    // - Test edge cases (invalid dates, overnight ranges)
    @Test
    @DisplayName("Should throw exception for invalid date")
    void testInvalidDate() {
        InvalidQueryException exception = assertThrows(InvalidQueryException.class,
            () -> queryParser.parse("2/30/2025"));
        assertTrue(exception.getMessage().contains("invalid date")
            || exception.getMessage().contains("Invalid date"));
    }

    @Test
    @DisplayName("Should handle overnight time ranges")
    void testOvernightTimeRange() {
        QueryCriteria criteria = queryParser.parse("between 22:00 and 6:00");
        assertEquals(QueryCriteria.QueryType.TIME_BASED, criteria.type());
        assertEquals(QueryCriteria.TimeRange.RangeType.BETWEEN, criteria.timeRange().type());
        assertEquals(LocalTime.of(22, 0), criteria.timeRange().start());
        assertEquals(LocalTime.of(6, 0), criteria.timeRange().end());
    }
}
