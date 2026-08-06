package com.cronquery.service.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalTime;
import java.util.List;

import com.cronquery.service.exception.InvalidQueryException;
import com.cronquery.service.parser.QueryCriteria.QueryType;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property-based tests for QueryParser component.
 * Feature: java-native-implementation
 * 
 * Tests Properties 3-7 from the design document's Correctness Properties section.
 */
public class QueryParserPropertyTest {

    private final QueryParser queryParser = new QueryParserImpl();

    // =========================================================================
    // Generators
    // =========================================================================

    /**
     * Generates valid day-based query strings that the parser should recognize.
     */
    @Provide
    Arbitrary<String> validDayQueries() {
        List<String> dayNames = List.of(
            "sunday", "sun", "monday", "mon", "tuesday", "tue", "tues",
            "wednesday", "wed", "thursday", "thu", "thur", "thurs",
            "friday", "fri", "saturday", "sat"
        );
        List<String> groups = List.of("weekdays", "weekends", "weekend", "weekday");
        List<String> relative = List.of("today", "tomorrow", "yesterday");
        List<String> modifiers = List.of("this", "next", "coming");

        Arbitrary<String> plainDays = Arbitraries.of(dayNames);
        Arbitrary<String> groupDays = Arbitraries.of(groups);
        Arbitrary<String> relativeDays = Arbitraries.of(relative);
        Arbitrary<String> modifiedDays = Arbitraries.of(modifiers)
            .flatMap(mod -> Arbitraries.of(dayNames).map(day -> mod + " " + day));

        return Arbitraries.oneOf(plainDays, groupDays, relativeDays, modifiedDays);
    }

    /**
     * Generates valid time-based query strings (12-hour and 24-hour formats).
     */
    @Provide
    Arbitrary<String> validTimeQueries() {
        // 12-hour format: "8 AM", "3:30 PM"
        Arbitrary<String> time12h = Combinators.combine(
            Arbitraries.integers().between(1, 12),
            Arbitraries.integers().between(0, 59),
            Arbitraries.of("am", "pm")
        ).as((hour, minute, ampm) -> {
            if (minute == 0) {
                return hour + " " + ampm;
            }
            return hour + ":" + String.format("%02d", minute) + " " + ampm;
        });

        // 24-hour format: "14:30", "9:00"
        Arbitrary<String> time24h = Combinators.combine(
            Arbitraries.integers().between(0, 23),
            Arbitraries.integers().between(0, 59)
        ).as((hour, minute) -> hour + ":" + String.format("%02d", minute));

        return Arbitraries.oneOf(time12h, time24h);
    }

    /**
     * Generates valid time range query strings.
     */
    @Provide
    Arbitrary<String> validTimeRangeQueries() {
        Arbitrary<String> timeExpr = validTimeQueries();

        Arbitrary<String> afterQueries = timeExpr.map(t -> "after " + t);
        Arbitrary<String> beforeQueries = timeExpr.map(t -> "before " + t);
        Arbitrary<String> betweenQueries = Combinators.combine(timeExpr, timeExpr)
            .as((t1, t2) -> "between " + t1 + " and " + t2);

        return Arbitraries.oneOf(afterQueries, beforeQueries, betweenQueries);
    }

    /**
     * Generates valid combined (day + time range) query strings.
     */
    @Provide
    Arbitrary<String> validCombinedQueries() {
        List<String> dayNames = List.of(
            "sunday", "monday", "tuesday", "wednesday",
            "thursday", "friday", "saturday",
            "weekdays", "weekends"
        );
        List<String> modifiers = List.of("this", "next", "coming");

        Arbitrary<String> timeExpr = validTimeQueries();

        // Pattern: "this Saturday after 10 AM"
        Arbitrary<String> modifiedDayWithTime = Combinators.combine(
            Arbitraries.of(modifiers),
            Arbitraries.of(dayNames.subList(0, 7)), // only actual day names for modifiers
            Arbitraries.of("after", "before"),
            timeExpr
        ).as((mod, day, rel, time) -> mod + " " + day + " " + rel + " " + time);

        // Pattern: "weekdays after 10 AM"
        Arbitrary<String> dayWithTime = Combinators.combine(
            Arbitraries.of(dayNames),
            Arbitraries.of("after", "before"),
            timeExpr
        ).as((day, rel, time) -> day + " " + rel + " " + time);

        return Arbitraries.oneOf(modifiedDayWithTime, dayWithTime);
    }

    /**
     * Generates recognizable query patterns (union of day, time, range, combined).
     */
    @Provide
    Arbitrary<String> recognizableQueries() {
        return Arbitraries.oneOf(
            validDayQueries(),
            validTimeQueries(),
            validTimeRangeQueries(),
            validCombinedQueries()
        );
    }

    /**
     * Generates random query strings for normalization testing.
     */
    @Provide
    Arbitrary<String> randomQueryStrings() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withChars(' ', ',', ';', ':', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
            .ofMinLength(1)
            .ofMaxLength(50);
    }

    /**
     * Generates equivalent time representations for the same time point.
     * Each entry is a pair: [time expression, expected hour, expected minute].
     */
    @Provide
    Arbitrary<TimeEquivalenceGroup> equivalentTimeFormats() {
        // Generate a random hour (1-12 range for 12-hour format, 0-23 for 24-hour)
        return Combinators.combine(
            Arbitraries.integers().between(1, 11), // hour 1-11 (avoid 12 for AM/PM edge case simplicity)
            Arbitraries.integers().between(0, 59),
            Arbitraries.of(true, false) // AM or PM
        ).as((hour12, minute, isAm) -> {
            int hour24 = isAm ? hour12 : hour12 + 12;
            String ampm = isAm ? "am" : "pm";

            // 12-hour without minutes: "8 pm"
            String format12hNoMin = hour12 + " " + ampm;
            // 12-hour with minutes: "8:00 pm"
            String format12hWithMin = hour12 + ":" + String.format("%02d", minute) + " " + ampm;
            // 24-hour: "20:00"
            String format24h = hour24 + ":" + String.format("%02d", minute);

            return new TimeEquivalenceGroup(
                format12hNoMin,
                format12hWithMin,
                format24h,
                LocalTime.of(hour24, minute)
            );
        });
    }

    /**
     * Generates invalid calendar dates that should not exist.
     */
    @Provide
    Arbitrary<String> invalidCalendarDates() {
        // Generate dates that are definitely invalid
        return Arbitraries.of(
            "2/30/2025",   // February 30th
            "2/29/2025",   // Feb 29 in non-leap year
            "4/31/2025",   // April 31st
            "6/31/2025",   // June 31st
            "9/31/2025",   // September 31st
            "11/31/2025",  // November 31st
            "13/1/2025",   // Month 13
            "0/15/2025"    // Month 0
        );
    }

    /**
     * Generates queries with mismatched day names and dates.
     * The day name does NOT match the actual day of the date.
     */
    @Provide
    Arbitrary<String> conflictingDayDateQueries() {
        // Use known dates where we know the day-of-week
        // 9/18/2025 is a Thursday, 9/20/2025 is a Saturday, 9/22/2025 is a Monday
        return Arbitraries.of(
            "Saturday 9/18/2025",    // 9/18/2025 is Thursday, not Saturday
            "Monday 9/18/2025",      // 9/18/2025 is Thursday, not Monday
            "Friday 9/18/2025",      // 9/18/2025 is Thursday, not Friday
            "Sunday 9/20/2025",      // 9/20/2025 is Saturday, not Sunday
            "Tuesday 9/20/2025",     // 9/20/2025 is Saturday, not Tuesday
            "Wednesday 9/22/2025",   // 9/22/2025 is Monday, not Wednesday
            "Thursday 9/22/2025",    // 9/22/2025 is Monday, not Thursday
            "Friday 9/22/2025"       // 9/22/2025 is Monday, not Friday
        );
    }

    // =========================================================================
    // Properties
    // =========================================================================

    /**
     * Feature: java-native-implementation, Property 3: Query Parser Completeness
     *
     * For any natural language query containing recognizable day or time patterns,
     * QueryParser SHALL extract the corresponding criteria and NOT return QueryType.UNKNOWN.
     *
     * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 10.1-10.6
     */
    @Property(tries = 100)
    @Tag("property-test")
    @Tag("query-parser")
    void queryParserCompleteness(@ForAll("recognizableQueries") String query) {
        QueryCriteria criteria = queryParser.parse(query);

        assertNotEquals(QueryType.UNKNOWN, criteria.type(),
            "Parser returned UNKNOWN for recognizable query: '" + query + "'");

        // Verify appropriate criteria fields are populated based on type
        switch (criteria.type()) {
            case DAY_BASED -> assertTrue(criteria.hasDayCriteria() || criteria.isSpecificDate(),
                "DAY_BASED query should have day criteria or specific date: '" + query + "'");
            case TIME_BASED -> assertTrue(criteria.hasTimeCriteria(),
                "TIME_BASED query should have time criteria: '" + query + "'");
            case COMBINED -> {
                assertTrue(criteria.hasDayCriteria() || criteria.isSpecificDate(),
                    "COMBINED query should have day criteria: '" + query + "'");
                assertTrue(criteria.hasTimeCriteria(),
                    "COMBINED query should have time criteria: '" + query + "'");
            }
            default -> fail("Unexpected query type: " + criteria.type());
        }
    }

    /**
     * Feature: java-native-implementation, Property 4: Query Normalization Idempotence
     *
     * For any query string, normalizing it twice SHALL produce the same result as
     * normalizing it once: normalize(normalize(q)) = normalize(q).
     *
     * Validates: Requirements 13.8
     */
    @Property(tries = 100)
    // @Report(Reporting.GENERATED)
    @Tag("property-test")
    @Tag("query-parser")
    void queryNormalizationIdempotence(@ForAll("randomQueryStrings") String query) {
        String normalizedOnce = queryParser.normalize(query);
        String normalizedTwice = queryParser.normalize(normalizedOnce);

        // System.out.println("TEMP DEBUG: Input: '" + query + "' → Normalized: '" + normalizedOnce + "'");

        assertEquals(normalizedOnce, normalizedTwice,
            "Normalization is not idempotent for query: '" + query + "'" +
            " → first: '" + normalizedOnce + "'" +
            " → second: '" + normalizedTwice + "'");
    }

    /**
     * Feature: java-native-implementation, Property 5: Time Format Normalization
     *
     * For any valid time specification in different formats (12-hour with AM/PM, 24-hour),
     * QueryParser SHALL normalize to a consistent LocalTime representation.
     * Example: "8 PM", "20:00", "8:00 PM" all produce LocalTime.of(20, 0).
     *
     * Validates: Requirements 10.7
     */
    @Property(tries = 100)
    @Tag("property-test")
    @Tag("query-parser")
    void timeFormatNormalization(@ForAll("equivalentTimeFormats") TimeEquivalenceGroup group) {
        // Parse the 12-hour format, no minute
        QueryCriteria criteria12hNoMin = queryParser.parse(group.format12hNoMin());
        // Parse the 12-hour format
        QueryCriteria criteria12h = queryParser.parse(group.format12h());
        // Parse the 24-hour format
        QueryCriteria criteria24h = queryParser.parse(group.format24h());

        assertNotNull(criteria12hNoMin.exactTime(),
            "Failed to parse 12-hour time, no minute: '" + group.format12hNoMin() + "'");
        assertNotNull(criteria12h.exactTime(),
            "Failed to parse 12-hour time: '" + group.format12h() + "'");
        assertNotNull(criteria24h.exactTime(),
            "Failed to parse 24-hour time: '" + group.format24h() + "'");

        // Both should produce the same LocalTime
        assertEquals(group.expectedTime(), criteria12h.exactTime(),
            "12-hour format '" + group.format12h() + "' did not produce expected time " + group.expectedTime());
        assertEquals(group.expectedTime(), criteria24h.exactTime(),
            "24-hour format '" + group.format24h() + "' did not produce expected time " + group.expectedTime());
        assertEquals(criteria12h.exactTime(), criteria24h.exactTime(),
            "12-hour '" + group.format12h() + "' and 24-hour '" + group.format24h() +
            "' produced different times: " + criteria12h.exactTime() + " vs " + criteria24h.exactTime());
        
        // "8 pm" always means hour:00 — test that hour-only implies :00
    LocalTime expectedHourOnly = LocalTime.of(group.expectedTime().getHour(), 0);
    assertEquals(expectedHourOnly, criteria12hNoMin.exactTime(),
        "12-hour no-minute format '" + group.format12hNoMin() + "' should imply :00, expected " + expectedHourOnly);
    }

    /**
     * Feature: java-native-implementation, Property 6: Date Validation
     *
     * For any explicit date string representing an invalid calendar date,
     * QueryParser SHALL throw InvalidQueryException.
     *
     * Validates: Requirements 13.10
     *
     * NOTE: This test validates functionality required by the spec. If the parser
     * does not yet implement date validation, this test will correctly fail,
     * indicating the feature needs implementation.
     */
    @Property(tries = 100)
    @Tag("property-test")
    @Tag("query-parser")
    void dateValidation(@ForAll("invalidCalendarDates") String invalidDate) {
        assertThrows(InvalidQueryException.class,
            () -> queryParser.parse(invalidDate),
            "Parser should reject invalid calendar date: '" + invalidDate + "'");
    }

    /**
     * Feature: java-native-implementation, Property 7: Day-Date Conflict Detection
     *
     * For any query combining a day name and explicit date where the date does not
     * fall on that day, QueryParser SHALL throw InvalidQueryException with a
     * descriptive conflict message.
     *
     * Validates: Requirements 13.3
     */
    @Property(tries = 100)
    @Tag("property-test")
    @Tag("query-parser")
    void dayDateConflictDetection(@ForAll("conflictingDayDateQueries") String conflictQuery) {
        InvalidQueryException exception = assertThrows(InvalidQueryException.class,
            () -> queryParser.parse(conflictQuery),
            "Parser should detect day-date conflict in: '" + conflictQuery + "'");

        // The error message should be descriptive about the conflict
        String message = exception.getMessage().toLowerCase();
        assertTrue(
            message.contains("conflict") || message.contains("not a") ||
            message.contains("is a") || message.contains("Date conflict"),
            "Exception message should describe the conflict for: '" + conflictQuery +
            "', got: '" + exception.getMessage() + "'");
    }

    // =========================================================================
    // Helper records
    // =========================================================================

    /**
     * Groups equivalent time format representations with their expected LocalTime.
     */
    record TimeEquivalenceGroup(
        String format12hNoMin,
        String format12h,
        String format24h,
        LocalTime expectedTime
    ) {}
}
