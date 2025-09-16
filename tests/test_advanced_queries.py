#!/usr/bin/env python3
"""
Unit tests for advanced query functionality (Task 7).

Tests the new relative date parsing, time range parsing, and combined queries.
"""

import unittest
from datetime import datetime, timedelta
from unittest.mock import patch
import sys
import os

# Add src to path for imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'src'))

from cron_query.query_parser import (
    parse_query, format_criteria_description, QueryType, QueryCriteria
)
from cron_query.cron_loader import parse_cron_line
from cron_query.schedule_analyzer import find_matching_jobs


class TestRelativeDateParsing(unittest.TestCase):
    """Test relative date parsing functionality."""
    
    def setUp(self):
        """Set up test with fixed current time."""
        # Mock current time to be Monday, September 16, 2025 at 12:00 PM
        self.mock_now = datetime(2025, 9, 16, 12, 0, 0)  # Monday
        
    @patch('cron_query.query_parser.datetime')
    def test_this_saturday(self, mock_datetime):
        """Test 'this Saturday' parsing."""
        mock_datetime.now.return_value = self.mock_now
        
        criteria = parse_query("this Saturday")
        
        self.assertEqual(criteria.query_type, QueryType.DAY_BASED)
        self.assertTrue(criteria.is_specific_date)
        self.assertEqual(criteria.days_of_week, {6})  # Saturday
        
        # Should be Saturday, September 20, 2025
        expected_date = datetime(2025, 9, 20, 0, 0, 0)
        self.assertEqual(criteria.specific_date, expected_date)
    
    @patch('cron_query.query_parser.datetime')
    def test_next_monday(self, mock_datetime):
        """Test 'next Monday' parsing."""
        mock_datetime.now.return_value = self.mock_now
        
        criteria = parse_query("next Monday")
        
        self.assertEqual(criteria.query_type, QueryType.DAY_BASED)
        self.assertTrue(criteria.is_specific_date)
        self.assertEqual(criteria.days_of_week, {1})  # Monday
        
        # Should be Monday, September 29, 2025 (next week, skipping this week)
        expected_date = datetime(2025, 9, 29, 0, 0, 0)
        self.assertEqual(criteria.specific_date, expected_date)
    
    @patch('cron_query.query_parser.datetime')
    def test_coming_weekend(self, mock_datetime):
        """Test 'coming weekend' parsing."""
        mock_datetime.now.return_value = self.mock_now
        
        criteria = parse_query("coming weekend")
        
        self.assertEqual(criteria.query_type, QueryType.DAY_BASED)
        self.assertFalse(criteria.is_specific_date)  # Weekend is not a single date
        self.assertEqual(criteria.days_of_week, {0, 6})  # Sunday, Saturday
        self.assertTrue(criteria.weekends_only)
    
    @patch('cron_query.query_parser.datetime')
    def test_typo_comming_saturday(self, mock_datetime):
        """Test 'comming Saturday' with typo."""
        mock_datetime.now.return_value = self.mock_now
        
        criteria = parse_query("comming Saturday")
        
        self.assertEqual(criteria.query_type, QueryType.DAY_BASED)
        self.assertTrue(criteria.is_specific_date)
        self.assertEqual(criteria.days_of_week, {6})  # Saturday
        
        # Should still work despite the typo
        expected_date = datetime(2025, 9, 20, 0, 0, 0)
        self.assertEqual(criteria.specific_date, expected_date)


class TestTimeRangeParsing(unittest.TestCase):
    """Test time range parsing functionality."""
    
    def test_after_time(self):
        """Test 'after X' time parsing."""
        criteria = parse_query("after 10 AM")
        
        self.assertEqual(criteria.query_type, QueryType.TIME_BASED)
        self.assertTrue(criteria.is_time_after)
        self.assertFalse(criteria.is_time_before)
        self.assertFalse(criteria.is_time_between)
        self.assertEqual(criteria.time_range_start, (10, 0))
        self.assertIsNone(criteria.time_range_end)
    
    def test_before_time(self):
        """Test 'before X' time parsing."""
        criteria = parse_query("before 5 PM")
        
        self.assertEqual(criteria.query_type, QueryType.TIME_BASED)
        self.assertFalse(criteria.is_time_after)
        self.assertTrue(criteria.is_time_before)
        self.assertFalse(criteria.is_time_between)
        self.assertIsNone(criteria.time_range_start)
        self.assertEqual(criteria.time_range_end, (17, 0))
    
    def test_between_times(self):
        """Test 'between X and Y' time parsing.""" 
        criteria = parse_query("between 9 AM and 5 PM")
        
        self.assertEqual(criteria.query_type, QueryType.TIME_BASED)
        self.assertFalse(criteria.is_time_after)
        self.assertFalse(criteria.is_time_before)
        self.assertTrue(criteria.is_time_between)
        self.assertEqual(criteria.time_range_start, (9, 0))
        self.assertEqual(criteria.time_range_end, (17, 0))
    
    def test_time_range_24_hour_format(self):
        """Test time ranges with 24-hour format."""
        criteria = parse_query("after 14:30")
        
        self.assertEqual(criteria.query_type, QueryType.TIME_BASED)
        self.assertTrue(criteria.is_time_after)
        self.assertEqual(criteria.time_range_start, (14, 30))
    
    def test_time_range_description(self):
        """Test time range descriptions."""
        test_cases = [
            ("after 10 AM", "after 10:00 AM"),
            ("before 5 PM", "before 5:00 PM"),  
            ("between 9 AM and 5 PM", "between 9:00 AM and 5:00 PM"),
        ]
        
        for query, expected_desc in test_cases:
            with self.subTest(query=query):
                criteria = parse_query(query)
                description = format_criteria_description(criteria)
                self.assertEqual(description, expected_desc)


class TestCombinedQueries(unittest.TestCase):
    """Test combined day + time queries."""
    
    @patch('cron_query.query_parser.datetime')
    def test_specific_day_after_time(self, mock_datetime):
        """Test 'this Saturday after 10 AM' parsing."""
        mock_datetime.now.return_value = datetime(2025, 9, 16, 12, 0, 0)  # Monday
        
        criteria = parse_query("this Saturday after 10 AM")
        
        self.assertEqual(criteria.query_type, QueryType.COMBINED)
        self.assertTrue(criteria.is_specific_date)
        self.assertEqual(criteria.days_of_week, {6})  # Saturday
        self.assertTrue(criteria.is_time_after)
        self.assertEqual(criteria.time_range_start, (10, 0))
        
        # Check description
        description = format_criteria_description(criteria)
        self.assertIn("Saturday, September 20", description)
        self.assertIn("after 10:00 AM", description)
    
    @patch('cron_query.query_parser.datetime')
    def test_query_with_comma(self, mock_datetime):
        """Test query with comma like 'this Saturday, after 10 AM'."""
        mock_datetime.now.return_value = datetime(2025, 9, 16, 12, 0, 0)  # Monday
        
        criteria = parse_query("this Saturday, after 10 AM")
        
        self.assertEqual(criteria.query_type, QueryType.COMBINED)
        self.assertTrue(criteria.is_specific_date)
        self.assertTrue(criteria.is_time_after)
        self.assertEqual(criteria.time_range_start, (10, 0))
    
    @patch('cron_query.query_parser.datetime') 
    def test_centos_problematic_query(self, mock_datetime):
        """Test the exact query that failed on CentOS."""
        mock_datetime.now.return_value = datetime(2025, 9, 16, 12, 0, 0)  # Monday
        
        # This is the exact query that caused issues
        criteria = parse_query("jobs this comming Saturday, after 10 am")
        
        self.assertEqual(criteria.query_type, QueryType.COMBINED)
        self.assertTrue(criteria.is_specific_date)
        self.assertEqual(criteria.days_of_week, {6})  # Saturday
        self.assertTrue(criteria.is_time_after)
        self.assertEqual(criteria.time_range_start, (10, 0))
        
        # Should handle the typo "comming" and the comma
        expected_date = datetime(2025, 9, 20, 0, 0, 0)
        self.assertEqual(criteria.specific_date, expected_date)
    
    def test_general_day_with_time_range(self):
        """Test general day queries with time ranges."""
        criteria = parse_query("weekends before 5 PM")
        
        self.assertEqual(criteria.query_type, QueryType.COMBINED)
        self.assertFalse(criteria.is_specific_date)
        self.assertTrue(criteria.weekends_only)
        self.assertEqual(criteria.days_of_week, {0, 6})  # Sunday, Saturday
        self.assertTrue(criteria.is_time_before)
        self.assertEqual(criteria.time_range_end, (17, 0))


class TestScheduleMatching(unittest.TestCase):
    """Test schedule matching with advanced queries."""
    
    def setUp(self):
        """Set up sample cron jobs."""
        self.sample_jobs = []
        cron_lines = [
            "0 8 * * 6 /path/to/saturday_morning_backup",     # Saturday 8 AM
            "0 12 * * 6 /path/to/saturday_noon_report",       # Saturday noon  
            "30 14 * * 6 /path/to/saturday_afternoon_task",   # Saturday 2:30 PM
            "0 9 * * 1-5 /path/to/weekday_morning_job",       # Weekday 9 AM
            "0 22 * * * /path/to/nightly_backup",             # Every day 10 PM
            "*/15 * * * * /path/to/frequent_check",           # Every 15 minutes
            "0 6,18 * * 0 /path/to/sunday_twice_daily",       # Sunday 6 AM & 6 PM
        ]
        
        for line in cron_lines:
            job = parse_cron_line(line)
            if job and job.is_valid:
                self.sample_jobs.append(job)
    
    def test_specific_date_matching(self):
        """Test matching jobs on specific dates with a constructed criteria.""" 
        # Instead of relying on relative date parsing, construct criteria directly
        # This tests the core specific date matching logic
        from cron_query.query_parser import QueryCriteria, QueryType
        
        # Create criteria for a specific Saturday (September 20, 2025)
        target_date = datetime(2025, 9, 20, 0, 0, 0)  # A Saturday
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="test specific Saturday",
            days_of_week={6},  # Saturday
            is_specific_date=True,
            specific_date=target_date
        )
        
        matching_jobs = find_matching_jobs(self.sample_jobs, criteria)
        
        # Should find Saturday jobs
        saturday_commands = [job.command for job in matching_jobs]
        self.assertIn("/path/to/saturday_morning_backup", saturday_commands)
        self.assertIn("/path/to/saturday_noon_report", saturday_commands) 
        self.assertIn("/path/to/saturday_afternoon_task", saturday_commands)
        
        # Should also include daily jobs that run on Saturday
        self.assertIn("/path/to/nightly_backup", saturday_commands)
        self.assertIn("/path/to/frequent_check", saturday_commands)
    
    def test_time_range_matching(self):
        """Test matching jobs with time ranges."""
        criteria = parse_query("after 10 AM")
        matching_jobs = find_matching_jobs(self.sample_jobs, criteria)
        
        # Should find jobs that run after 10 AM
        commands = [job.command for job in matching_jobs]
        self.assertIn("/path/to/saturday_noon_report", commands)      # 12 PM
        self.assertIn("/path/to/saturday_afternoon_task", commands)   # 2:30 PM  
        self.assertIn("/path/to/nightly_backup", commands)           # 10 PM
        self.assertIn("/path/to/frequent_check", commands)           # Every 15 min (includes times after 10 AM)
        
        # Should NOT include early morning jobs
        self.assertNotIn("/path/to/saturday_morning_backup", commands)  # 8 AM
        self.assertNotIn("/path/to/weekday_morning_job", commands)      # 9 AM
    
    def test_between_time_range_matching(self):
        """Test matching jobs with 'between' time ranges."""
        criteria = parse_query("between 8 AM and 3 PM")
        matching_jobs = find_matching_jobs(self.sample_jobs, criteria)
        
        commands = [job.command for job in matching_jobs]
        self.assertIn("/path/to/saturday_morning_backup", commands)    # 8 AM
        self.assertIn("/path/to/weekday_morning_job", commands)        # 9 AM
        self.assertIn("/path/to/saturday_noon_report", commands)       # 12 PM
        self.assertIn("/path/to/saturday_afternoon_task", commands)    # 2:30 PM
        self.assertIn("/path/to/frequent_check", commands)             # Every 15 min (includes business hours)
        
        # Should NOT include late jobs
        self.assertNotIn("/path/to/nightly_backup", commands)         # 10 PM
    
    def test_combined_specific_date_and_time_range(self):
        """Test combined specific date + time range queries."""
        # Create combined criteria directly to test the core logic
        from cron_query.query_parser import QueryCriteria, QueryType
        
        # Create criteria for Saturday after 10 AM
        target_date = datetime(2025, 9, 20, 0, 0, 0)  # A Saturday
        criteria = QueryCriteria(
            query_type=QueryType.COMBINED,
            raw_query="test Saturday after 10 AM",
            days_of_week={6},  # Saturday
            is_specific_date=True,
            specific_date=target_date,
            time_range_start=(10, 0),  # 10:00 AM
            is_time_after=True
        )
        
        matching_jobs = find_matching_jobs(self.sample_jobs, criteria)
        commands = [job.command for job in matching_jobs]
        
        # Should find Saturday jobs after 10 AM
        self.assertIn("/path/to/saturday_noon_report", commands)      # Saturday 12 PM ✓
        self.assertIn("/path/to/saturday_afternoon_task", commands)   # Saturday 2:30 PM ✓
        self.assertIn("/path/to/nightly_backup", commands)           # Daily 10 PM (includes Saturday) ✓
        self.assertIn("/path/to/frequent_check", commands)           # Every 15 min (includes Sat after 10 AM) ✓
        
        # Should NOT include Saturday jobs before 10 AM
        self.assertNotIn("/path/to/saturday_morning_backup", commands)  # Saturday 8 AM ❌
        
        # Should NOT include weekday-only jobs
        self.assertNotIn("/path/to/weekday_morning_job", commands)      # Mon-Fri 9 AM ❌


class TestEdgeCases(unittest.TestCase):
    """Test edge cases and error handling."""
    
    def test_invalid_time_ranges(self):
        """Test invalid time range queries."""
        # These should still parse but might not match sensibly
        criteria = parse_query("after 25:00")  # Invalid 24-hour time
        self.assertEqual(criteria.query_type, QueryType.UNKNOWN)
        
        criteria = parse_query("before 13 PM")  # Invalid 12-hour time  
        self.assertEqual(criteria.query_type, QueryType.UNKNOWN)
    
    def test_malformed_combined_queries(self):
        """Test malformed combined queries."""
        # Should gracefully handle malformed queries
        criteria = parse_query("this Saturday and after")
        # Should not crash, might parse as day-only or unknown
        self.assertIn(criteria.query_type, [QueryType.DAY_BASED, QueryType.UNKNOWN])
    
    def test_complex_punctuation(self):
        """Test queries with complex punctuation."""
        criteria = parse_query("this Saturday, after 10:30 AM")
        
        self.assertEqual(criteria.query_type, QueryType.COMBINED)
        self.assertTrue(criteria.is_specific_date)
        self.assertTrue(criteria.is_time_after)
        self.assertEqual(criteria.time_range_start, (10, 30))


if __name__ == '__main__':
    unittest.main()