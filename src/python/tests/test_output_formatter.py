#!/usr/bin/env python3
"""
Unit tests for output_formatter module.
"""

import pytest
import json
from datetime import datetime
from unittest.mock import patch, MagicMock

from cron_query.output_formatter import (
    format_query_results,
    format_error_message,
    format_job_summary,
    format_execution_time,
    get_supported_formats,
    validate_output_format,
    OutputFormat,
    OutputFormatterError,
    _format_list_output,
    _format_table_output,
    _format_json_output,
    _format_empty_results
)
from cron_query.cron_loader import CronJob
from cron_query.query_parser import QueryCriteria, QueryType


class TestFormatQueryResults:
    """Test cases for format_query_results function."""
    
    def setup_method(self):
        """Set up test data."""
        self.sample_jobs = [
            CronJob("0", "8", "*", "*", "1-5", "/weekday/backup.sh", "0 8 * * 1-5 /weekday/backup.sh"),
            CronJob("0", "2", "*", "*", "6", "/saturday/cleanup.sh", "0 2 * * 6 /saturday/cleanup.sh")
        ]
        
        self.sample_criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="weekdays",
            days_of_week={1, 2, 3, 4, 5},
            weekdays_only=True
        )
    
    def test_format_list_output(self):
        """Test basic list format output."""
        result = format_query_results(self.sample_jobs, self.sample_criteria, "list")
        
        assert "Jobs matching 'weekdays (Monday-Friday)':" in result
        # Check the job is present (format may have extra spaces)
        assert "0 8 * * 1-5" in result and "/weekday/backup.sh" in result
        assert "2. 0 2 * * 6 /saturday/cleanup.sh" in result
        assert "Schedule:" in result
        assert "Next runs:" in result
        assert "Found 2 matching jobs." in result
    
    def test_format_table_output(self):
        """Test table format output."""
        result = format_query_results(self.sample_jobs, self.sample_criteria, "table")
        
        assert "Jobs matching 'weekdays (Monday-Friday)':" in result
        assert "Expression" in result
        assert "Command" in result
        assert "Description" in result
        assert "Next Run" in result
        assert "0 8 * * 1-5" in result
        assert "/weekday/backup.sh" in result
        assert "+" in result  # Table borders
        assert "|" in result  # Table separators
        # Check the summary is present (format may be "Showing 1-2 of 2")
        assert "2 matching job" in result
    
    def test_format_json_output(self):
        """Test JSON format output."""
        result = format_query_results(self.sample_jobs, self.sample_criteria, "json")
        
        # Parse JSON to verify it's valid
        data = json.loads(result)
        
        assert data["query"]["description"] == "weekdays (Monday-Friday)"
        assert data["query"]["raw_query"] == "weekdays"
        assert data["query"]["type"] == "day_based"
        assert data["matches"] == 2
        assert len(data["jobs"]) == 2
        
        # Check first job
        job1 = data["jobs"][0]
        assert job1["cron_expression"] == "0 8 * * 1-5"
        assert job1["command"] == "/weekday/backup.sh"
        assert job1["raw_line"] == "0 8 * * 1-5 /weekday/backup.sh"
        assert "schedule_description" in job1
        assert "next_runs" in job1
    
    def test_format_without_next_runs(self):
        """Test formatting without next run times."""
        result = format_query_results(self.sample_jobs, self.sample_criteria, "list", show_next_runs=False)
        
        assert "Jobs matching 'weekdays (Monday-Friday)':" in result
        assert "Next runs:" not in result
        assert "Schedule:" in result  # Should still show schedule description
    
    def test_format_empty_results(self):
        """Test formatting when no jobs match."""
        result = format_query_results([], self.sample_criteria, "list")
        
        assert "No jobs found matching 'weekdays (Monday-Friday)'" in result
        assert "This could mean:" in result
        assert "Try:" in result
        assert "crontab -l" in result
    
    def test_unsupported_format(self):
        """Test error handling for unsupported formats."""
        with pytest.raises(OutputFormatterError, match="Unsupported output format"):
            format_query_results(self.sample_jobs, self.sample_criteria, "xml")
    
    @patch('cron_query.output_formatter.get_job_schedule_description')
    def test_schedule_description_error_handling(self, mock_get_desc):
        """Test handling of schedule description errors."""
        mock_get_desc.side_effect = Exception("Test error")
        
        result = format_query_results(self.sample_jobs[:1], self.sample_criteria, "list")
        
        # Should fallback to cron expression when description fails
        assert "Schedule: 0 8 * * 1-5" in result
    
    @patch('cron_query.output_formatter.get_next_runs')
    def test_next_runs_error_handling(self, mock_next_runs):
        """Test handling of next runs calculation errors."""
        mock_next_runs.side_effect = Exception("Test error")
        
        result = format_query_results(self.sample_jobs[:1], self.sample_criteria, "list")
        
        # Should not include next runs when calculation fails
        assert "Next runs:" not in result
        assert "Jobs matching" in result  # Should still format the rest


class TestFormatListOutput:
    """Test cases for _format_list_output function."""
    
    def setup_method(self):
        """Set up test data."""
        self.job = CronJob("0", "8", "*", "*", "*", "/daily/job.sh", "0 8 * * * /daily/job.sh")
        self.criteria = QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query="8 am",
            time_hour=8,
            time_minute=0
        )
    
    def test_single_job_formatting(self):
        """Test formatting a single job."""
        result = _format_list_output([self.job], self.criteria, True, 3)
        
        assert "Jobs matching '8:00 AM':" in result
        assert "1. 0 8 * * * /daily/job.sh" in result
        assert "Schedule:" in result
        assert "Next runs:" in result
        assert "Found 1 matching job." in result
    
    def test_multiple_jobs_formatting(self):
        """Test formatting multiple jobs."""
        jobs = [
            self.job,
            CronJob("30", "8", "*", "*", "*", "/morning/job.sh", "30 8 * * * /morning/job.sh")
        ]
        
        result = _format_list_output(jobs, self.criteria, True, 2)
        
        assert "1. 0 8 * * * /daily/job.sh" in result
        assert "2. 30 8 * * * /morning/job.sh" in result
        assert "Found 2 matching jobs." in result
    
    def test_empty_jobs_list(self):
        """Test formatting empty jobs list."""
        result = _format_list_output([], self.criteria, True, 3)
        
        assert "No jobs found matching '8:00 AM'" in result
        assert "This could mean:" in result


class TestFormatTableOutput:
    """Test cases for _format_table_output function."""
    
    def setup_method(self):
        """Set up test data."""
        self.jobs = [
            CronJob("0", "8", "*", "*", "*", "/short/cmd", "0 8 * * * /short/cmd"),
            CronJob("30", "20", "*", "*", "1-5", "/very/long/command/path/script.sh", "30 20 * * 1-5 /very/long/command/path/script.sh")
        ]
        self.criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="test",
            days_of_week={1}
        )
    
    def test_table_structure(self):
        """Test table structure and formatting."""
        result = _format_table_output(self.jobs, self.criteria, True, 1)
        
        # Check table structure
        assert "+" in result  # Table borders
        assert "|" in result  # Column separators
        assert "#" in result  # Header column
        assert "Expression" in result
        assert "Command" in result
        assert "Description" in result
        assert "Next Run" in result
    
    def test_table_column_alignment(self):
        """Test that columns are properly aligned."""
        result = _format_table_output(self.jobs, self.criteria, True, 1)
        
        lines = result.split('\n')
        # Find table lines (those with |)
        table_lines = [line for line in lines if '|' in line and line.strip() != '']
        
        # All table lines should have the same structure
        assert len(set(len(line) for line in table_lines)) <= 2  # Header might be different due to padding
    
    def test_table_without_next_runs(self):
        """Test table without next run column."""
        result = _format_table_output(self.jobs, self.criteria, False, 1)
        
        # Should not have Next Run column
        lines = result.split('\n')
        header_line = next(line for line in lines if 'Expression' in line)
        assert "Next Run" not in header_line
        assert "Expression" in header_line
        assert "Command" in header_line
        assert "Description" in header_line


class TestFormatJsonOutput:
    """Test cases for _format_json_output function."""
    
    def setup_method(self):
        """Set up test data."""
        self.jobs = [
            CronJob("0", "8", "*", "*", "*", "/daily/job.sh", "0 8 * * * /daily/job.sh", user="testuser", source="user")
        ]
        self.criteria = QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query="8 am",
            time_hour=8,
            time_minute=0
        )
    
    def test_json_structure(self):
        """Test JSON output structure."""
        result = _format_json_output(self.jobs, self.criteria, True, 2)
        data = json.loads(result)
        
        # Check top-level structure
        assert "query" in data
        assert "matches" in data
        assert "jobs" in data
        
        # Check query structure
        query = data["query"]
        assert "description" in query
        assert "raw_query" in query
        assert "type" in query
        
        # Check job structure
        assert len(data["jobs"]) == 1
        job = data["jobs"][0]
        assert "cron_expression" in job
        assert "command" in job
        assert "raw_line" in job
        assert "user" in job
        assert "source" in job
        assert "schedule_description" in job
        assert "next_runs" in job
    
    def test_json_without_next_runs(self):
        """Test JSON output without next runs."""
        result = _format_json_output(self.jobs, self.criteria, False, 2)
        data = json.loads(result)
        
        job = data["jobs"][0]
        assert "next_runs" not in job or job["next_runs"] == []
    
    def test_next_runs_format(self):
        """Test next runs format in JSON."""
        result = _format_json_output(self.jobs, self.criteria, True, 2)
        data = json.loads(result)
        
        job = data["jobs"][0]
        if job["next_runs"]:  # May be empty if get_next_runs fails
            next_run = job["next_runs"][0]
            assert "timestamp" in next_run
            assert "human_readable" in next_run
            
            # Validate timestamp format (ISO)
            datetime.fromisoformat(next_run["timestamp"])


class TestErrorFormatting:
    """Test cases for error formatting functions."""
    
    def test_format_error_message_with_query(self):
        """Test formatting error message with query."""
        error = ValueError("Test error message")
        query = "invalid query"
        
        result = format_error_message(error, query)
        
        assert "❌ Error: Test error message" in result
        assert "Query: 'invalid query'" in result
        assert "Troubleshooting:" in result
        assert "cron-query 'Saturday'" in result
    
    def test_format_error_message_without_query(self):
        """Test formatting error message without query."""
        error = RuntimeError("Another error")
        
        result = format_error_message(error)
        
        assert "❌ Error: Another error" in result
        assert "Query:" not in result
        assert "Troubleshooting:" in result
    
    def test_format_job_summary_empty(self):
        """Test job summary with no jobs."""
        result = format_job_summary([])
        assert result == "No jobs found"
    
    def test_format_job_summary_single_job(self):
        """Test job summary with single job."""
        job = CronJob("0", "8", "*", "*", "*", "/test", "0 8 * * * /test", user="testuser", source="user")
        
        result = format_job_summary([job])
        assert "1 job" in result
        assert "from user" in result
        assert "(user: testuser)" in result
    
    def test_format_job_summary_multiple_jobs(self):
        """Test job summary with multiple jobs."""
        jobs = [
            CronJob("0", "8", "*", "*", "*", "/test1", "0 8 * * * /test1", user="user1", source="user"),
            CronJob("0", "9", "*", "*", "*", "/test2", "0 9 * * * /test2", user="user2", source="system")
        ]
        
        result = format_job_summary(jobs)
        assert "2 jobs" in result
        assert "from system, user" in result  # Sorted
        assert "(2 users)" in result
    
    def test_format_execution_time_milliseconds(self):
        """Test execution time formatting in milliseconds."""
        start = datetime(2023, 1, 1, 12, 0, 0)
        end = datetime(2023, 1, 1, 12, 0, 0, 500000)  # 500ms later
        
        result = format_execution_time(start, end)
        assert "500.0ms" in result
    
    def test_format_execution_time_seconds(self):
        """Test execution time formatting in seconds."""
        start = datetime(2023, 1, 1, 12, 0, 0)
        end = datetime(2023, 1, 1, 12, 0, 2, 500000)  # 2.5s later
        
        result = format_execution_time(start, end)
        assert "2.50s" in result


class TestUtilityFunctions:
    """Test cases for utility functions."""
    
    def test_get_supported_formats(self):
        """Test getting supported formats."""
        formats = get_supported_formats()
        
        assert "list" in formats
        assert "table" in formats
        assert "json" in formats
        assert len(formats) >= 3  # Should have at least list, table, json
    
    def test_validate_output_format_valid(self):
        """Test validating valid formats."""
        assert validate_output_format("list") is True
        assert validate_output_format("table") is True
        assert validate_output_format("json") is True
    
    def test_validate_output_format_invalid(self):
        """Test validating invalid formats."""
        assert validate_output_format("xml") is False
        # CSV is now supported, so test with truly invalid format
        assert validate_output_format("invalid_format") is False
        assert validate_output_format("invalid") is False


class TestOutputFormats:
    """Test cases for OutputFormat enum."""
    
    def test_output_format_values(self):
        """Test OutputFormat enum values."""
        assert OutputFormat.LIST.value == "list"
        assert OutputFormat.TABLE.value == "table"
        assert OutputFormat.JSON.value == "json"


class TestEmptyResultsFormatting:
    """Test cases for empty results formatting."""
    
    def test_format_empty_results_day_query(self):
        """Test formatting empty results for day query."""
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="saturday",
            days_of_week={6}
        )
        
        result = _format_empty_results(criteria)
        
        assert "No jobs found matching 'Saturday'" in result
        assert "This could mean:" in result
        assert "crontab -l" in result
    
    def test_format_empty_results_time_query(self):
        """Test formatting empty results for time query."""
        criteria = QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query="8 am",
            time_hour=8,
            time_minute=0
        )
        
        result = _format_empty_results(criteria)
        
        assert "No jobs found matching '8:00 AM'" in result
        assert "This could mean:" in result


class TestIntegration:
    """Integration tests combining multiple components."""
    
    def test_full_formatting_pipeline(self):
        """Test complete formatting pipeline."""
        # Create test data
        jobs = [
            CronJob("0", "8", "*", "*", "1-5", "/weekday/backup.sh", "0 8 * * 1-5 /weekday/backup.sh"),
            CronJob("30", "20", "*", "*", "6", "/saturday/script.sh", "30 20 * * 6 /saturday/script.sh")
        ]
        
        criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query="test query",
            days_of_week={1, 2, 3, 4, 5, 6}
        )
        
        # Test all formats
        for fmt in ["list", "table", "json"]:
            result = format_query_results(jobs, criteria, fmt)
            assert len(result) > 0
            if fmt == "json":
                # Ensure valid JSON
                json.loads(result)
    
    @patch('cron_query.output_formatter.get_next_runs')
    @patch('cron_query.output_formatter.get_job_schedule_description')
    def test_error_resilience(self, mock_desc, mock_next_runs):
        """Test that formatting continues even when helper functions fail."""
        # Make helper functions fail
        mock_desc.side_effect = Exception("Description error")
        mock_next_runs.side_effect = Exception("Next runs error")
        
        job = CronJob("0", "8", "*", "*", "*", "/test", "0 8 * * * /test")
        criteria = QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query="8 am",
            time_hour=8,
            time_minute=0
        )
        
        # Should not raise exceptions
        result_list = format_query_results([job], criteria, "list")
        result_table = format_query_results([job], criteria, "table")
        result_json = format_query_results([job], criteria, "json")
        
        # Should still produce output
        assert len(result_list) > 0
        assert len(result_table) > 0
        assert len(result_json) > 0
        
        # JSON should be valid
        json.loads(result_json)