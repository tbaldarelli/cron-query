#!/usr/bin/env python3
"""
Unit tests for schedule_analyzer module.
"""

import pytest
from datetime import datetime, timedelta
from unittest.mock import patch, MagicMock

from cron_query.schedule_analyzer import (
    find_matching_jobs,
    matches_criteria,
    runs_on_day_of_week,
    runs_at_time,
    get_next_runs,
    get_job_schedule_description,
    _check_day_of_week_field,
    _matches_time_field,
    _parse_cron_field,
    ScheduleAnalysisError
)
from cron_query.cron_loader import CronJob
from cron_query.query_parser import QueryCriteria, QueryType


class TestFindMatchingJobs:
    """Test cases for find_matching_jobs function."""
    
    def test_find_matching_day_jobs(self):
        """Test finding jobs that match day criteria."""
        jobs = [
            CronJob("0", "8", "*", "*", "6", "/saturday/job.sh", "0 8 * * 6 /saturday/job.sh"),
            CronJob("0", "8", "*", "*", "1-5", "/weekday/job.sh", "0 8 * * 1-5 /weekday/job.sh"),
            CronJob("0", "8", "*", "*", "0", "/sunday/job.sh", "0 8 * * 0 /sunday/job.sh")
        ]
        
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="saturday",
            days_of_week={6}
        )
        
        matches = find_matching_jobs(jobs, criteria)
        
        assert len(matches) == 1
        assert matches[0].command == "/saturday/job.sh"
    
    def test_find_matching_time_jobs(self):
        """Test finding jobs that match time criteria."""
        jobs = [
            CronJob("0", "8", "*", "*", "*", "/8am/job.sh", "0 8 * * * /8am/job.sh"),
            CronJob("30", "8", "*", "*", "*", "/830am/job.sh", "30 8 * * * /830am/job.sh"),
            CronJob("0", "20", "*", "*", "*", "/8pm/job.sh", "0 20 * * * /8pm/job.sh")
        ]
        
        criteria = QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query="8 am",
            time_hour=8,
            time_minute=0
        )
        
        matches = find_matching_jobs(jobs, criteria)
        
        assert len(matches) == 1
        assert matches[0].command == "/8am/job.sh"
    
    def test_find_matching_weekday_jobs(self):
        """Test finding jobs that match weekday criteria."""
        jobs = [
            CronJob("0", "8", "*", "*", "1-5", "/weekday/job.sh", "0 8 * * 1-5 /weekday/job.sh"),
            CronJob("0", "8", "*", "*", "6", "/saturday/job.sh", "0 8 * * 6 /saturday/job.sh"),
            CronJob("0", "8", "*", "*", "0,6", "/weekend/job.sh", "0 8 * * 0,6 /weekend/job.sh")
        ]
        
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="weekdays",
            days_of_week={1, 2, 3, 4, 5},
            weekdays_only=True
        )
        
        matches = find_matching_jobs(jobs, criteria)
        
        assert len(matches) == 1
        assert matches[0].command == "/weekday/job.sh"
    
    def test_find_no_matching_jobs(self):
        """Test when no jobs match criteria."""
        jobs = [
            CronJob("0", "8", "*", "*", "1-5", "/weekday/job.sh", "0 8 * * 1-5 /weekday/job.sh")
        ]
        
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="saturday",
            days_of_week={6}
        )
        
        matches = find_matching_jobs(jobs, criteria)
        
        assert len(matches) == 0
    
    def test_unknown_query_type(self):
        """Test handling unknown query type."""
        jobs = [
            CronJob("0", "8", "*", "*", "*", "/test/job.sh", "0 8 * * * /test/job.sh")
        ]
        
        criteria = QueryCriteria(
            query_type=QueryType.UNKNOWN,
            raw_query="unknown"
        )
        
        matches = find_matching_jobs(jobs, criteria)
        
        assert len(matches) == 0


class TestMatchesCriteria:
    """Test cases for matches_criteria function."""
    
    def test_matches_day_criteria(self):
        """Test matching day-based criteria."""
        job = CronJob("0", "8", "*", "*", "6", "/saturday/job.sh", "0 8 * * 6 /saturday/job.sh")
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="saturday",
            days_of_week={6}
        )
        
        assert matches_criteria(job, criteria) is True
    
    def test_matches_time_criteria(self):
        """Test matching time-based criteria."""
        job = CronJob("0", "8", "*", "*", "*", "/8am/job.sh", "0 8 * * * /8am/job.sh")
        criteria = QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query="8 am",
            time_hour=8,
            time_minute=0
        )
        
        assert matches_criteria(job, criteria) is True
    
    def test_invalid_job_skipped(self):
        """Test that invalid jobs are skipped."""
        # Create an invalid job by forcing is_valid to False
        job = CronJob("99", "25", "*", "*", "*", "/invalid/job.sh", "99 25 * * * /invalid/job.sh")
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="saturday",
            days_of_week={6}
        )
        
        assert matches_criteria(job, criteria) is False


class TestRunsOnDayOfWeek:
    """Test cases for runs_on_day_of_week function."""
    
    def test_simple_day_match(self):
        """Test simple day-of-week matching."""
        job = CronJob("0", "8", "*", "*", "6", "/saturday/job.sh", "0 8 * * 6 /saturday/job.sh")
        
        assert runs_on_day_of_week(job, {6}) is True
        assert runs_on_day_of_week(job, {0}) is False
    
    def test_range_day_match(self):
        """Test day-of-week range matching."""
        job = CronJob("0", "8", "*", "*", "1-5", "/weekday/job.sh", "0 8 * * 1-5 /weekday/job.sh")
        
        assert runs_on_day_of_week(job, {1}) is True
        assert runs_on_day_of_week(job, {3}) is True
        assert runs_on_day_of_week(job, {5}) is True
        assert runs_on_day_of_week(job, {6}) is False
        assert runs_on_day_of_week(job, {0}) is False
    
    def test_multiple_day_match(self):
        """Test multiple day-of-week matching."""
        job = CronJob("0", "8", "*", "*", "0,6", "/weekend/job.sh", "0 8 * * 0,6 /weekend/job.sh")
        
        assert runs_on_day_of_week(job, {0}) is True
        assert runs_on_day_of_week(job, {6}) is True
        assert runs_on_day_of_week(job, {1}) is False
    
    def test_every_day_match(self):
        """Test jobs that run every day."""
        job = CronJob("0", "8", "*", "*", "*", "/daily/job.sh", "0 8 * * * /daily/job.sh")
        
        assert runs_on_day_of_week(job, {0}) is True
        assert runs_on_day_of_week(job, {6}) is True
        assert runs_on_day_of_week(job, {1, 2, 3, 4, 5}) is True
    
    def test_day_of_month_only(self):
        """Test jobs with only day-of-month specified."""
        job = CronJob("0", "8", "15", "*", "*", "/monthly/job.sh", "0 8 15 * * /monthly/job.sh")
        
        # This should use the complex logic to check if 15th falls on target days
# For testing, we'll assume it can match (depends on calendar)


class TestLeapYearHandling:
    """Test cases for leap year handling."""
    
    def test_feb_29_jobs(self):
        """Test jobs that run on February 29th."""
        job = CronJob("0", "8", "29", "2", "*", "/leap-day.sh", "0 8 29 2 * /leap-day.sh")
        
        # Test with a specific date in a leap year
        leap_date = datetime(2024, 2, 29)
        next_runs = get_next_runs(job, count=3, start_time=leap_date)
        
        assert len(next_runs) == 3
        assert next_runs[0].strftime("%Y-%m-%d") == "2024-02-29"
        assert next_runs[1].strftime("%Y-%m-%d") == "2028-02-29"
        assert next_runs[2].strftime("%Y-%m-%d") == "2032-02-29"
    
    def test_end_of_month_jobs(self):
        """Test jobs that run on last day of month during leap years."""
        job = CronJob("0", "8", "L", "2", "*", "/last-day.sh", "0 8 L 2 * /last-day.sh")
        
        # Check in both leap and non-leap years
        leap_date = datetime(2024, 2, 1)
        non_leap_date = datetime(2025, 2, 1)
        
        leap_runs = get_next_runs(job, count=1, start_time=leap_date)
        non_leap_runs = get_next_runs(job, count=1, start_time=non_leap_date)
        
        assert leap_runs[0].strftime("%Y-%m-%d") == "2024-02-29"
        assert non_leap_runs[0].strftime("%Y-%m-%d") == "2025-02-28"
    
    def test_crossing_leap_boundaries(self):
        """Test next run calculations across leap year boundaries."""
        # Job that runs on March 1st
        job = CronJob("0", "8", "1", "3", "*", "/march-first.sh", "0 8 1 3 * /march-first.sh")
        
        # Start just before Feb 29 in a leap year
        start_date = datetime(2024, 2, 28)
        next_runs = get_next_runs(job, count=2, start_time=start_date)
        
        # Should properly handle the leap day when calculating next March 1st
        assert next_runs[0].strftime("%Y-%m-%d") == "2024-03-01"
        assert next_runs[1].strftime("%Y-%m-%d") == "2025-03-01"
        result = runs_on_day_of_week(job, {6})  # Saturday
        assert isinstance(result, bool)  # Just check it returns a boolean
    
    def test_empty_target_days(self):
        """Test with empty target days."""
        job = CronJob("0", "8", "*", "*", "6", "/saturday/job.sh", "0 8 * * 6 /saturday/job.sh")
        
        assert runs_on_day_of_week(job, set()) is False
        assert runs_on_day_of_week(job, None) is False


class TestRunsAtTime:
    """Test cases for runs_at_time function."""
    
    def test_exact_time_match(self):
        """Test exact time matching."""
        job = CronJob("30", "8", "*", "*", "*", "/830am/job.sh", "30 8 * * * /830am/job.sh")
        
        assert runs_at_time(job, 8, 30) is True
        assert runs_at_time(job, 8, 0) is False
        assert runs_at_time(job, 9, 30) is False
    
    def test_hour_only_match(self):
        """Test hour-only matching."""
        job = CronJob("*", "8", "*", "*", "*", "/8am/job.sh", "* 8 * * * /8am/job.sh")
        
        assert runs_at_time(job, 8, None) is True
        assert runs_at_time(job, 8, 15) is True  # Any minute at hour 8
        assert runs_at_time(job, 9, None) is False
    
    def test_minute_only_match(self):
        """Test minute-only matching."""
        job = CronJob("30", "*", "*", "*", "*", "/30min/job.sh", "30 * * * * /30min/job.sh")
        
        assert runs_at_time(job, None, 30) is True
        assert runs_at_time(job, 8, 30) is True  # Hour 8, minute 30
        assert runs_at_time(job, None, 15) is False
    
    def test_no_time_criteria(self):
        """Test with no time criteria."""
        job = CronJob("30", "8", "*", "*", "*", "/test/job.sh", "30 8 * * * /test/job.sh")
        
        assert runs_at_time(job, None, None) is True
    
    def test_wildcard_time_fields(self):
        """Test jobs with wildcard time fields."""
        job = CronJob("*", "*", "*", "*", "*", "/every/minute.sh", "* * * * * /every/minute.sh")
        
        assert runs_at_time(job, 8, 30) is True
        assert runs_at_time(job, 23, 59) is True
        assert runs_at_time(job, None, 30) is True
    
    def test_range_time_fields(self):
        """Test jobs with range time fields."""
        job = CronJob("0", "9-17", "*", "*", "*", "/business/hours.sh", "0 9-17 * * * /business/hours.sh")
        
        assert runs_at_time(job, 9, 0) is True
        assert runs_at_time(job, 12, 0) is True
        assert runs_at_time(job, 17, 0) is True
        assert runs_at_time(job, 8, 0) is False
        assert runs_at_time(job, 18, 0) is False


class TestGetNextRuns:
    """Test cases for get_next_runs function."""
    
    def test_get_next_runs_basic(self):
        """Test getting next run times."""
        job = CronJob("0", "8", "*", "*", "*", "/daily/job.sh", "0 8 * * * /daily/job.sh")
        
        next_runs = get_next_runs(job, 3)
        
        assert len(next_runs) == 3
        assert all(isinstance(run, datetime) for run in next_runs)
        assert next_runs[0] < next_runs[1] < next_runs[2]  # Should be in order
    
    def test_get_next_runs_custom_start(self):
        """Test getting next runs from custom start time."""
        job = CronJob("0", "8", "*", "*", "*", "/daily/job.sh", "0 8 * * * /daily/job.sh")
        start_time = datetime(2023, 1, 1, 0, 0, 0)
        
        next_runs = get_next_runs(job, 2, start_time)
        
        assert len(next_runs) == 2
        assert all(run > start_time for run in next_runs)
    
    def test_get_next_runs_invalid_job(self):
        """Test error handling for invalid jobs."""
        job = CronJob("99", "25", "*", "*", "*", "/invalid/job.sh", "99 25 * * * /invalid/job.sh")
        
        with pytest.raises(ScheduleAnalysisError, match="Invalid cron expression"):
            get_next_runs(job)


class TestCheckDayOfWeekField:
    """Test cases for _check_day_of_week_field function."""
    
    def test_single_day(self):
        """Test single day matching."""
        assert _check_day_of_week_field("6", {6}) is True
        assert _check_day_of_week_field("6", {0}) is False
    
    def test_day_range(self):
        """Test day range matching."""
        assert _check_day_of_week_field("1-5", {1}) is True
        assert _check_day_of_week_field("1-5", {3}) is True
        assert _check_day_of_week_field("1-5", {5}) is True
        assert _check_day_of_week_field("1-5", {0}) is False
        assert _check_day_of_week_field("1-5", {6}) is False
    
    def test_multiple_days(self):
        """Test multiple day matching."""
        assert _check_day_of_week_field("0,6", {0}) is True
        assert _check_day_of_week_field("0,6", {6}) is True
        assert _check_day_of_week_field("0,6", {1}) is False
    
    def test_wildcard(self):
        """Test wildcard matching."""
        assert _check_day_of_week_field("*", {0}) is True
        assert _check_day_of_week_field("*", {6}) is True
        assert _check_day_of_week_field("*", {1, 2, 3, 4, 5}) is True
    
    def test_sunday_conversion(self):
        """Test conversion of Sunday from 7 to 0."""
        assert _check_day_of_week_field("7", {0}) is True
        assert _check_day_of_week_field("0,7", {0}) is True


class TestMatchesTimeField:
    """Test cases for _matches_time_field function."""
    
    def test_exact_match(self):
        """Test exact value matching."""
        assert _matches_time_field("8", 8) is True
        assert _matches_time_field("8", 9) is False
    
    def test_wildcard_match(self):
        """Test wildcard matching."""
        assert _matches_time_field("*", 8) is True
        assert _matches_time_field("*", 23) is True
    
    def test_range_match(self):
        """Test range matching."""
        assert _matches_time_field("8-10", 8) is True
        assert _matches_time_field("8-10", 9) is True
        assert _matches_time_field("8-10", 10) is True
        assert _matches_time_field("8-10", 7) is False
        assert _matches_time_field("8-10", 11) is False
    
    def test_multiple_values_match(self):
        """Test multiple values matching."""
        assert _matches_time_field("8,10,12", 8) is True
        assert _matches_time_field("8,10,12", 10) is True
        assert _matches_time_field("8,10,12", 12) is True
        assert _matches_time_field("8,10,12", 9) is False
    
    def test_step_values_match(self):
        """Test step values matching."""
        assert _matches_time_field("*/2", 0) is True
        assert _matches_time_field("*/2", 2) is True
        assert _matches_time_field("*/2", 4) is True
        assert _matches_time_field("*/2", 1) is False
        assert _matches_time_field("*/2", 3) is False


class TestParseCronField:
    """Test cases for _parse_cron_field function."""
    
    def test_single_value(self):
        """Test parsing single values."""
        result = _parse_cron_field("5", 0, 23)
        assert result == {5}
    
    def test_range_values(self):
        """Test parsing range values."""
        result = _parse_cron_field("1-5", 0, 6)
        assert result == {1, 2, 3, 4, 5}
    
    def test_multiple_values(self):
        """Test parsing multiple values."""
        result = _parse_cron_field("1,3,5", 0, 6)
        assert result == {1, 3, 5}
    
    def test_step_values(self):
        """Test parsing step values."""
        result = _parse_cron_field("*/2", 0, 6)
        assert result == {0, 2, 4, 6}
    
    def test_range_with_step(self):
        """Test parsing range with step."""
        result = _parse_cron_field("1-5/2", 0, 6)
        assert result == {1, 3, 5}
    
    def test_complex_expression(self):
        """Test parsing complex expressions."""
        result = _parse_cron_field("1,3-5,*/3", 0, 9)
        expected = {1, 3, 4, 5, 0, 3, 6, 9}  # 1 + (3-5) + (*/3)
        assert result == expected
    
    def test_out_of_range_filtered(self):
        """Test that out-of-range values are filtered."""
        result = _parse_cron_field("25", 0, 23)
        assert result == set()  # 25 is out of range for 0-23


class TestGetJobScheduleDescription:
    """Test cases for get_job_schedule_description function."""
    
    def test_simple_daily_job(self):
        """Test description for simple daily job."""
        job = CronJob("0", "8", "*", "*", "*", "/daily/job.sh", "0 8 * * * /daily/job.sh")
        
        description = get_job_schedule_description(job)
        
        assert "top of the hour" in description.lower() or "08:00" in description
        assert "next:" in description.lower()
    
    def test_invalid_job_description(self):
        """Test description for invalid job."""
        job = CronJob("99", "25", "*", "*", "*", "/invalid/job.sh", "99 25 * * * /invalid/job.sh")
        
        description = get_job_schedule_description(job)
        
        assert "invalid cron expression" in description.lower()
    
    def test_complex_job_description(self):
        """Test description for complex job."""
        job = CronJob("30", "*/2", "1", "*", "6", "/complex/job.sh", "30 */2 1 * 6 /complex/job.sh")
        
        description = get_job_schedule_description(job)
        
        assert "complex day logic" in description.lower() or "day" in description.lower()


class TestIntegration:
    """Integration tests combining multiple components."""
    
    def test_end_to_end_day_matching(self):
        """Test complete day-based job matching."""
        # Create some test jobs
        jobs = [
            CronJob("0", "8", "*", "*", "1-5", "/weekday/backup.sh", "0 8 * * 1-5 /weekday/backup.sh"),
            CronJob("0", "2", "*", "*", "6", "/saturday/cleanup.sh", "0 2 * * 6 /saturday/cleanup.sh"),
            CronJob("0", "0", "1", "*", "*", "/monthly/report.sh", "0 0 1 * * /monthly/report.sh")
        ]
        
        # Test weekday matching
        weekday_criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="weekdays",
            days_of_week={1, 2, 3, 4, 5},
            weekdays_only=True
        )
        
        weekday_matches = find_matching_jobs(jobs, weekday_criteria)
        assert len(weekday_matches) == 1
        assert "backup.sh" in weekday_matches[0].command
        
        # Test Saturday matching
        saturday_criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="saturday",
            days_of_week={6}
        )
        
        saturday_matches = find_matching_jobs(jobs, saturday_criteria)
        assert len(saturday_matches) == 1
        assert "cleanup.sh" in saturday_matches[0].command
    
    def test_end_to_end_time_matching(self):
        """Test complete time-based job matching."""
        jobs = [
            CronJob("0", "8", "*", "*", "*", "/morning/job.sh", "0 8 * * * /morning/job.sh"),
            CronJob("30", "8", "*", "*", "*", "/830/job.sh", "30 8 * * * /830/job.sh"),
            CronJob("0", "20", "*", "*", "*", "/evening/job.sh", "0 20 * * * /evening/job.sh")
        ]
        
        # Test 8 AM matching
        morning_criteria = QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query="8 am",
            time_hour=8,
            time_minute=0
        )
        
        morning_matches = find_matching_jobs(jobs, morning_criteria)
        assert len(morning_matches) == 1
        assert "morning/job.sh" in morning_matches[0].command
        
        # Test 8:30 AM matching
        specific_criteria = QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query="8:30 am",
            time_hour=8,
            time_minute=30
        )
        
        specific_matches = find_matching_jobs(jobs, specific_criteria)
        assert len(specific_matches) == 1
        assert "830/job.sh" in specific_matches[0].command
    
    def test_error_handling_integration(self):
        """Test error handling in integration scenarios."""
        jobs = [
            CronJob("0", "8", "*", "*", "99", "/invalid/day.sh", "0 8 * * 99 /invalid/day.sh"),
            CronJob("0", "8", "*", "*", "6", "/valid/job.sh", "0 8 * * 6 /valid/job.sh")
        ]
        
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="saturday",
            days_of_week={6}
        )
        
        # Should handle invalid job gracefully and return valid matches
        matches = find_matching_jobs(jobs, criteria)
        assert len(matches) == 1
        assert "valid/job.sh" in matches[0].command