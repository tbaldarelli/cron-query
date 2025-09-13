#!/usr/bin/env python3
"""
Unit tests for query_parser module.
"""

import pytest
from cron_query.query_parser import (
    QueryCriteria,
    QueryType,
    QueryParseError,
    parse_query,
    parse_day_query,
    parse_time_query,
    format_criteria_description,
    _normalize_query,
    DAY_NAMES,
    WEEKDAYS,
    WEEKENDS
)


class TestQueryCriteria:
    """Test cases for QueryCriteria data class."""
    
    def test_valid_criteria_creation(self):
        """Test creating valid QueryCriteria objects."""
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="saturday",
            days_of_week={6}
        )
        
        assert criteria.query_type == QueryType.DAY_BASED
        assert criteria.raw_query == "saturday"
        assert criteria.days_of_week == {6}
        assert criteria.time_hour is None
        assert criteria.time_minute is None
        assert criteria.is_specific_date is False
        assert criteria.weekdays_only is False
        assert criteria.weekends_only is False
    
    def test_time_criteria_creation(self):
        """Test creating time-based QueryCriteria."""
        criteria = QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query="8 AM",
            time_hour=8,
            time_minute=0
        )
        
        assert criteria.query_type == QueryType.TIME_BASED
        assert criteria.time_hour == 8
        assert criteria.time_minute == 0
    
    def test_invalid_hour_validation(self):
        """Test validation of invalid hours."""
        with pytest.raises(ValueError, match="Invalid hour: 25"):
            QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query="test",
                time_hour=25
            )
        
        with pytest.raises(ValueError, match="Invalid hour: -1"):
            QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query="test",
                time_hour=-1
            )
    
    def test_invalid_minute_validation(self):
        """Test validation of invalid minutes."""
        with pytest.raises(ValueError, match="Invalid minute: 60"):
            QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query="test",
                time_minute=60
            )
        
        with pytest.raises(ValueError, match="Invalid minute: -1"):
            QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query="test",
                time_minute=-1
            )
    
    def test_invalid_day_of_week_validation(self):
        """Test validation of invalid days of week."""
        with pytest.raises(ValueError, match="Invalid day of week: 7"):
            QueryCriteria(
                query_type=QueryType.DAY_BASED,
                raw_query="test",
                days_of_week={7}
            )
        
        with pytest.raises(ValueError, match="Invalid day of week: -1"):
            QueryCriteria(
                query_type=QueryType.DAY_BASED,
                raw_query="test",
                days_of_week={-1}
            )


class TestParseQuery:
    """Test cases for parse_query function."""
    
    def test_parse_empty_query(self):
        """Test parsing empty queries."""
        with pytest.raises(QueryParseError, match="Empty query"):
            parse_query("")
        
        with pytest.raises(QueryParseError, match="Empty query"):
            parse_query("   ")
    
    def test_parse_day_query_delegation(self):
        """Test that parse_query correctly delegates to parse_day_query."""
        result = parse_query("Saturday")
        
        assert result.query_type == QueryType.DAY_BASED
        assert result.days_of_week == {6}
    
    def test_parse_time_query_delegation(self):
        """Test that parse_query correctly delegates to parse_time_query."""
        result = parse_query("8 AM")
        
        assert result.query_type == QueryType.TIME_BASED
        assert result.time_hour == 8
        assert result.time_minute == 0
    
    def test_parse_unknown_query(self):
        """Test parsing queries that don't match any pattern."""
        result = parse_query("some random text")
        
        assert result.query_type == QueryType.UNKNOWN
        assert result.raw_query == "some random text"


class TestParseDayQuery:
    """Test cases for parse_day_query function."""
    
    def test_parse_specific_days(self):
        """Test parsing specific day names."""
        test_cases = [
            ("Saturday", {6}),
            ("sunday", {0}),
            ("MONDAY", {1}),
            ("tue", {2}),
            ("Wed", {3}),
            ("thursday", {4}),
            ("Fri", {5})
        ]
        
        for query, expected_days in test_cases:
            result = parse_day_query(query)
            
            assert result is not None
            assert result.query_type == QueryType.DAY_BASED
            assert result.days_of_week == expected_days
            assert result.is_specific_date is False
    
    def test_parse_multiple_days(self):
        """Test parsing queries with multiple days."""
        result = parse_day_query("Monday and Friday")
        
        assert result is not None
        assert result.days_of_week == {1, 5}
    
    def test_parse_weekdays(self):
        """Test parsing weekdays queries."""
        test_cases = [
            "weekdays",
            "weekday",
            "week days",
            "which jobs run on weekdays"
        ]
        
        for query in test_cases:
            result = parse_day_query(query)
            
            assert result is not None
            assert result.query_type == QueryType.DAY_BASED
            assert result.days_of_week == WEEKDAYS
            assert result.weekdays_only is True
    
    def test_parse_weekends(self):
        """Test parsing weekends queries."""
        test_cases = [
            "weekends",
            "weekend",
            "week ends",
            "which jobs run on weekends"
        ]
        
        for query in test_cases:
            result = parse_day_query(query)
            
            assert result is not None
            assert result.query_type == QueryType.DAY_BASED
            assert result.days_of_week == WEEKENDS
            assert result.weekends_only is True
    
    def test_parse_specific_date_queries(self):
        """Test parsing queries for specific dates."""
        test_cases = [
            "this Saturday",
            "next Monday",
            "coming Friday"
        ]
        
        for query in test_cases:
            result = parse_day_query(query)
            
            assert result is not None
            assert result.is_specific_date is True
    
    def test_parse_non_day_query(self):
        """Test that non-day queries return None."""
        non_day_queries = [
            "8 AM",
            "some random text",
            "the quick brown fox"
        ]
        
        for query in non_day_queries:
            result = parse_day_query(query)
            assert result is None
    
    def test_parse_invalid_day_query(self):
        """Test error handling for invalid day queries."""
        with pytest.raises(QueryParseError, match="Could not parse day query"):
            parse_day_query("on some invalid day")


class TestParseTimeQuery:
    """Test cases for parse_time_query function."""
    
    def test_parse_am_pm_format(self):
        """Test parsing AM/PM time format."""
        test_cases = [
            ("8 AM", 8, 0),
            ("8 PM", 20, 0),
            ("12 AM", 0, 0),  # Midnight
            ("12 PM", 12, 0),  # Noon
            ("1 AM", 1, 0),
            ("11 PM", 23, 0)
        ]
        
        for query, expected_hour, expected_minute in test_cases:
            result = parse_time_query(query)
            
            assert result is not None
            assert result.query_type == QueryType.TIME_BASED
            assert result.time_hour == expected_hour
            assert result.time_minute == expected_minute
    
    def test_parse_am_pm_with_minutes(self):
        """Test parsing AM/PM format with minutes."""
        test_cases = [
            ("8:30 AM", 8, 30),
            ("8:30 PM", 20, 30),
            ("12:15 AM", 0, 15),
            ("12:45 PM", 12, 45),
            ("1:05 AM", 1, 5),
            ("11:59 PM", 23, 59)
        ]
        
        for query, expected_hour, expected_minute in test_cases:
            result = parse_time_query(query)
            
            assert result is not None
            assert result.time_hour == expected_hour
            assert result.time_minute == expected_minute
    
    def test_parse_24_hour_format(self):
        """Test parsing 24-hour time format."""
        test_cases = [
            ("00:00", 0, 0),
            ("08:30", 8, 30),
            ("12:00", 12, 0),
            ("20:30", 20, 30),
            ("23:59", 23, 59)
        ]
        
        for query, expected_hour, expected_minute in test_cases:
            result = parse_time_query(query)
            
            assert result is not None
            assert result.time_hour == expected_hour
            assert result.time_minute == expected_minute
    
    def test_parse_hour_only_format(self):
        """Test parsing hour-only formats."""
        test_cases = [
            ("at 8", 8, 0),
            ("8 o'clock", 8, 0),
            ("at 14", 14, 0)
        ]
        
        for query, expected_hour, expected_minute in test_cases:
            result = parse_time_query(query)
            
            assert result is not None
            assert result.time_hour == expected_hour
            assert result.time_minute == expected_minute
    
    def test_parse_complex_time_queries(self):
        """Test parsing time queries with additional words."""
        test_cases = [
            ("which jobs run at 8 AM", 8, 0),
            ("jobs that run at 20:30", 20, 30),
            ("show me jobs at 8:30 PM", 20, 30)
        ]
        
        for query, expected_hour, expected_minute in test_cases:
            result = parse_time_query(query)
            
            assert result is not None
            assert result.time_hour == expected_hour
            assert result.time_minute == expected_minute
    
    def test_parse_non_time_query(self):
        """Test that non-time queries return None."""
        non_time_queries = [
            "Saturday",
            "some random text",
            "the quick brown fox"
        ]
        
        for query in non_time_queries:
            result = parse_time_query(query)
            assert result is None
    
    def test_parse_invalid_time_queries(self):
        """Test error handling for invalid time queries."""
        invalid_queries = [
            ("13 AM", "Invalid hour for 12-hour format"),
            ("0 PM", "Invalid hour for 12-hour format"),
            ("25:00", "Invalid hour for 24-hour format"),
            ("8:60", "Invalid minute"),
            ("at 25", "Invalid hour")
        ]
        
        for query, expected_error in invalid_queries:
            with pytest.raises(QueryParseError, match=expected_error):
                parse_time_query(query)
    
    def test_parse_ambiguous_time_queries(self):
        """Test handling of queries that look like time but can't be parsed."""
        with pytest.raises(QueryParseError, match="Could not parse time query"):
            parse_time_query("at some invalid time")


class TestNormalizeQuery:
    """Test cases for _normalize_query function."""
    
    def test_remove_common_prefixes(self):
        """Test removal of common query prefixes."""
        test_cases = [
            ("which jobs run on Saturday", "saturday"),
            ("what jobs run at 8 AM", "at 8 am"),  # 'at' preserved with numbers
            ("show me jobs that run on weekdays", "weekdays"),
            ("jobs that run on Friday", "friday")
        ]
        
        for original, expected in test_cases:
            result = _normalize_query(original)
            assert result == expected
    
    def test_remove_prepositions(self):
        """Test removal of prepositions at the beginning."""
        test_cases = [
            ("on Saturday", "saturday"),
            ("at 8 AM", "at 8 am"),  # 'at' preserved with numbers
            ("in the morning", "morning"),
            ("during weekdays", "weekdays")
        ]
        
        for original, expected in test_cases:
            result = _normalize_query(original)
            assert result == expected
    
    def test_preserve_meaningful_words(self):
        """Test that meaningful words are preserved."""
        test_cases = [
            ("Saturday morning", "saturday morning"),  # 'morning' is preserved
            ("next Friday", "next friday"),  # 'next' is preserved
            ("8:30 PM", "8:30 pm")  # Time format preserved
        ]
        
        for original, expected in test_cases:
            result = _normalize_query(original)
            assert result == expected


class TestFormatCriteriaDescription:
    """Test cases for format_criteria_description function."""
    
    def test_format_day_based_single_day(self):
        """Test formatting single day descriptions."""
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="saturday",
            days_of_week={6}
        )
        
        result = format_criteria_description(criteria)
        assert result == "Saturday"
    
    def test_format_day_based_multiple_days(self):
        """Test formatting multiple days descriptions."""
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="monday and friday",
            days_of_week={1, 5}
        )
        
        result = format_criteria_description(criteria)
        assert result == "Monday and Friday"
    
    def test_format_weekdays(self):
        """Test formatting weekdays description."""
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="weekdays",
            days_of_week=WEEKDAYS.copy(),
            weekdays_only=True
        )
        
        result = format_criteria_description(criteria)
        assert result == "weekdays (Monday-Friday)"
    
    def test_format_weekends(self):
        """Test formatting weekends description."""
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="weekends",
            days_of_week=WEEKENDS.copy(),
            weekends_only=True
        )
        
        result = format_criteria_description(criteria)
        assert result == "weekends (Saturday-Sunday)"
    
    def test_format_time_based_am(self):
        """Test formatting AM time descriptions."""
        test_cases = [
            (8, 0, "8:00 AM"),
            (0, 0, "midnight (12:00 AM)"),
            (0, 30, "12:30 AM"),
            (11, 45, "11:45 AM")
        ]
        
        for hour, minute, expected in test_cases:
            criteria = QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query="test",
                time_hour=hour,
                time_minute=minute
            )
            
            result = format_criteria_description(criteria)
            assert result == expected
    
    def test_format_time_based_pm(self):
        """Test formatting PM time descriptions."""
        test_cases = [
            (12, 0, "noon (12:00 PM)"),
            (12, 30, "12:30 PM"),
            (13, 0, "1:00 PM"),
            (20, 30, "8:30 PM"),
            (23, 59, "11:59 PM")
        ]
        
        for hour, minute, expected in test_cases:
            criteria = QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query="test",
                time_hour=hour,
                time_minute=minute
            )
            
            result = format_criteria_description(criteria)
            assert result == expected
    
    def test_format_unknown_query(self):
        """Test formatting unknown query descriptions."""
        criteria = QueryCriteria(
            query_type=QueryType.UNKNOWN,
            raw_query="some unknown query"
        )
        
        result = format_criteria_description(criteria)
        assert result == "unknown query: 'some unknown query'"


class TestConstants:
    """Test cases for module constants."""
    
    def test_day_names_mapping(self):
        """Test that DAY_NAMES contains expected mappings."""
        # Test full day names
        assert DAY_NAMES['sunday'] == 0
        assert DAY_NAMES['monday'] == 1
        assert DAY_NAMES['tuesday'] == 2
        assert DAY_NAMES['wednesday'] == 3
        assert DAY_NAMES['thursday'] == 4
        assert DAY_NAMES['friday'] == 5
        assert DAY_NAMES['saturday'] == 6
        
        # Test abbreviations
        assert DAY_NAMES['sun'] == 0
        assert DAY_NAMES['mon'] == 1
        assert DAY_NAMES['tue'] == 2
        assert DAY_NAMES['wed'] == 3
        assert DAY_NAMES['thu'] == 4
        assert DAY_NAMES['fri'] == 5
        assert DAY_NAMES['sat'] == 6
    
    def test_weekdays_constant(self):
        """Test WEEKDAYS constant."""
        assert WEEKDAYS == {1, 2, 3, 4, 5}  # Monday-Friday
    
    def test_weekends_constant(self):
        """Test WEEKENDS constant."""
        assert WEEKENDS == {0, 6}  # Saturday, Sunday


class TestIntegration:
    """Integration tests combining multiple components."""
    
    def test_full_parsing_pipeline(self):
        """Test the complete parsing pipeline."""
        test_cases = [
            # Day queries
            ("which jobs run on Saturday", QueryType.DAY_BASED, {6}),
            ("jobs that run on weekdays", QueryType.DAY_BASED, WEEKDAYS),
            ("what runs on weekends", QueryType.DAY_BASED, WEEKENDS),
            
            # Time queries
            ("which jobs run at 8 AM", QueryType.TIME_BASED, None),
            ("jobs at 20:30", QueryType.TIME_BASED, None),
            ("what runs at 8:30 PM", QueryType.TIME_BASED, None)
        ]
        
        for query, expected_type, expected_days in test_cases:
            result = parse_query(query)
            
            assert result.query_type == expected_type
            if expected_days is not None:
                assert result.days_of_week == expected_days
    
    def test_description_formatting_integration(self):
        """Test integration between parsing and description formatting."""
        test_cases = [
            ("Saturday", "Saturday"),
            ("weekdays", "weekdays (Monday-Friday)"),
            ("8 AM", "8:00 AM"),
            ("20:30", "8:30 PM")
        ]
        
        for query, expected_description in test_cases:
            criteria = parse_query(query)
            description = format_criteria_description(criteria)
            assert description == expected_description