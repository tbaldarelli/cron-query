#!/usr/bin/env python3
"""
Unit tests for cron_loader module.
"""

import pytest
import sys
from unittest.mock import patch, MagicMock
import subprocess

from cron_query.cron_loader import (
    CronJob,
    CronParseError,
    parse_cron_line,
    load_user_crontab,
    _validate_cron_fields,
    SPECIAL_KEYWORDS
)


class TestCronJob:
    """Test cases for CronJob data class."""
    
    def test_cron_job_creation(self):
        """Test basic CronJob creation."""
        job = CronJob(
            minute="0",
            hour="8", 
            day_of_month="*",
            month="*",
            day_of_week="1-5",
            command="/path/to/script.sh",
            raw_line="0 8 * * 1-5 /path/to/script.sh"
        )
        
        assert job.minute == "0"
        assert job.hour == "8"
        assert job.day_of_month == "*"
        assert job.month == "*"
        assert job.day_of_week == "1-5"
        assert job.command == "/path/to/script.sh"
        assert job.raw_line == "0 8 * * 1-5 /path/to/script.sh"
        assert job.source == "user"  # default
        assert job.user is None  # default
    
    def test_cron_expression_property(self):
        """Test cron_expression property."""
        job = CronJob(
            minute="30",
            hour="14", 
            day_of_month="15",
            month="*",
            day_of_week="*",
            command="/test/cmd",
            raw_line="test"
        )
        
        assert job.cron_expression == "30 14 15 * *"
    
    def test_is_valid_property_valid(self):
        """Test is_valid property with valid cron expression."""
        job = CronJob(
            minute="0",
            hour="8", 
            day_of_month="*",
            month="*",
            day_of_week="*",
            command="/test/cmd",
            raw_line="test"
        )
        
        assert job.is_valid is True
    
    def test_is_valid_property_invalid(self):
        """Test is_valid property with invalid cron expression."""
        job = CronJob(
            minute="99",  # Invalid minute
            hour="8", 
            day_of_month="*",
            month="*",
            day_of_week="*",
            command="/test/cmd",
            raw_line="test"
        )
        
        assert job.is_valid is False


class TestParseCronLine:
    """Test cases for parse_cron_line function."""
    
    def test_parse_valid_standard_cron(self):
        """Test parsing valid standard cron format."""
        line = "0 8 * * 1-5 /home/user/weekday-script.sh"
        job = parse_cron_line(line)
        
        assert job is not None
        assert job.minute == "0"
        assert job.hour == "8"
        assert job.day_of_month == "*"
        assert job.month == "*"
        assert job.day_of_week == "1-5"
        assert job.command == "/home/user/weekday-script.sh"
        assert job.raw_line == line
        assert job.source == "user"
    
    def test_parse_complex_cron_expression(self):
        """Test parsing complex cron expressions."""
        line = "*/15 9-17 1,15 */2 1,3,5 /complex/script.sh --arg value"
        job = parse_cron_line(line)
        
        assert job is not None
        assert job.minute == "*/15"
        assert job.hour == "9-17"
        assert job.day_of_month == "1,15"
        assert job.month == "*/2"
        assert job.day_of_week == "1,3,5"
        assert job.command == "/complex/script.sh --arg value"
    
    def test_parse_special_keywords(self):
        """Test parsing special cron keywords."""
        test_cases = [
            ("@daily /daily/task.sh", "0 0 * * *"),
            ("@hourly /hourly/task.sh", "0 * * * *"),
            ("@weekly /weekly/task.sh", "0 0 * * 0"),
            ("@monthly /monthly/task.sh", "0 0 1 * *"),
            ("@yearly /yearly/task.sh", "0 0 1 1 *"),
            ("@annually /annual/task.sh", "0 0 1 1 *"),
        ]
        
        for line, expected_expr in test_cases:
            job = parse_cron_line(line)
            
            assert job is not None
            assert job.cron_expression == expected_expr
            assert job.raw_line == line
    
    def test_parse_skip_comments(self):
        """Test that comments are skipped."""
        test_lines = [
            "# This is a comment",
            "  # Indented comment",
            "#Another comment"
        ]
        
        for line in test_lines:
            result = parse_cron_line(line)
            assert result is None
    
    def test_parse_skip_empty_lines(self):
        """Test that empty lines are skipped."""
        test_lines = ["", "   ", "\t", "\n"]
        
        for line in test_lines:
            result = parse_cron_line(line)
            assert result is None
    
    def test_parse_skip_environment_variables(self):
        """Test that environment variables are skipped."""
        test_lines = [
            "MAILTO=user@example.com",
            "PATH=/usr/bin:/bin",
            "SHELL=/bin/bash"
        ]
        
        for line in test_lines:
            result = parse_cron_line(line)
            assert result is None
    
    def test_parse_invalid_format_too_few_fields(self):
        """Test error handling for too few fields."""
        with pytest.raises(CronParseError, match="expected 6 or 7 fields, got 3"):
            parse_cron_line("0 8 *")
    
    def test_parse_invalid_special_keyword(self):
        """Test error handling for invalid special keywords."""
        with pytest.raises(CronParseError, match="Unknown special keyword: @invalid"):
            parse_cron_line("@invalid /some/command")
    
    def test_parse_invalid_special_keyword_format(self):
        """Test error handling for malformed special keyword."""
        with pytest.raises(CronParseError, match="Invalid special keyword format"):
            parse_cron_line("@daily")  # Missing command
    
    def test_parse_with_custom_source_and_user(self):
        """Test parsing with custom source and user."""
        line = "0 8 * * * /test/cmd"
        job = parse_cron_line(line, source="system", user="testuser")
        
        assert job.source == "system"
        assert job.user == "testuser"
    
    def test_parse_invalid_cron_expression(self):
        """Test error handling for invalid cron expressions."""
        # This should fail croniter validation
        with pytest.raises(CronParseError, match="Invalid cron expression"):
            parse_cron_line("99 25 32 13 8 /invalid/cmd")


class TestValidateCronFields:
    """Test cases for _validate_cron_fields function."""
    
    def test_validate_valid_fields(self):
        """Test validation with valid fields."""
        # Should not raise any exception
        _validate_cron_fields("0", "8", "*", "*", "1-5")
        _validate_cron_fields("*/15", "9-17", "1,15", "*/2", "1,3,5")
    
    def test_validate_empty_field(self):
        """Test validation with empty fields."""
        with pytest.raises(CronParseError, match="Empty minute field"):
            _validate_cron_fields("", "8", "*", "*", "*")
        
        with pytest.raises(CronParseError, match="Empty hour field"):
            _validate_cron_fields("0", "", "*", "*", "*")
    
    def test_validate_whitespace_field(self):
        """Test validation with whitespace-only fields."""
        with pytest.raises(CronParseError, match="Empty day_of_month field"):
            _validate_cron_fields("0", "8", "   ", "*", "*")


class TestLoadUserCrontab:
    """Test cases for load_user_crontab function."""
    
    @patch('cron_query.cron_loader.sys.platform', 'win32')
    def test_load_user_crontab_windows_mock(self):
        """Test loading user crontab on Windows (mock data)."""
        jobs = load_user_crontab()
        
        # Should return mock data
        assert isinstance(jobs, list)
        assert len(jobs) > 0
        
        # Verify we got some expected jobs from mock data
        commands = [job.command for job in jobs]
        assert "/home/user/backup.sh" in commands
        assert "/home/user/daily-cleanup.sh" in commands
    
    @patch('cron_query.cron_loader.sys.platform', 'linux')
    @patch('cron_query.cron_loader.subprocess.run')
    def test_load_user_crontab_linux_success(self, mock_run):
        """Test loading user crontab on Linux (successful)."""
        # Mock successful crontab output
        mock_result = MagicMock()
        mock_result.stdout = """# User crontab
0 8 * * 1-5 /home/user/weekday.sh
@daily /home/user/cleanup.sh
"""
        mock_result.returncode = 0
        mock_run.return_value = mock_result
        
        jobs = load_user_crontab()
        
        assert len(jobs) == 2
        assert jobs[0].command == "/home/user/weekday.sh"
        assert jobs[1].command == "/home/user/cleanup.sh"
        
        # Verify correct command was called
        mock_run.assert_called_once_with(
            ['crontab', '-l'],
            capture_output=True,
            text=True,
            check=True
        )
    
    @patch('cron_query.cron_loader.sys.platform', 'linux')
    @patch('cron_query.cron_loader.subprocess.run')
    def test_load_user_crontab_linux_with_user(self, mock_run):
        """Test loading specific user's crontab on Linux."""
        mock_result = MagicMock()
        mock_result.stdout = "0 8 * * * /test/cmd"
        mock_result.returncode = 0
        mock_run.return_value = mock_result
        
        jobs = load_user_crontab(user="testuser")
        
        # Verify correct command was called with user flag
        mock_run.assert_called_once_with(
            ['crontab', '-l', '-u', 'testuser'],
            capture_output=True,
            text=True,
            check=True
        )
    
    @patch('cron_query.cron_loader.sys.platform', 'linux')
    @patch('cron_query.cron_loader.subprocess.run')
    def test_load_user_crontab_no_crontab(self, mock_run):
        """Test handling when user has no crontab."""
        # Mock "no crontab" error
        error = subprocess.CalledProcessError(1, ['crontab', '-l'])
        error.stderr = "no crontab for user"
        mock_run.side_effect = error
        
        jobs = load_user_crontab()
        
        # Should return empty list, not raise exception
        assert jobs == []
    
    @patch('cron_query.cron_loader.sys.platform', 'linux')
    @patch('cron_query.cron_loader.subprocess.run')
    def test_load_user_crontab_other_error(self, mock_run):
        """Test handling of other crontab errors."""
        # Mock other error (should be re-raised)
        error = subprocess.CalledProcessError(2, ['crontab', '-l'])
        error.stderr = "permission denied"
        mock_run.side_effect = error
        
        with pytest.raises(subprocess.CalledProcessError):
            load_user_crontab()
    
    @patch('cron_query.cron_loader.sys.platform', 'linux')
    @patch('cron_query.cron_loader.subprocess.run')
    def test_load_user_crontab_parse_errors(self, mock_run):
        """Test handling parse errors in crontab output."""
        # Mock crontab with some invalid lines
        mock_result = MagicMock()
        mock_result.stdout = """# Valid crontab
0 8 * * * /valid/cmd
invalid line here
@daily /another/valid/cmd
"""
        mock_result.returncode = 0
        mock_run.return_value = mock_result
        
        jobs = load_user_crontab()
        
        # Should get the valid jobs only
        assert len(jobs) == 2
        assert jobs[0].command == "/valid/cmd"
        assert jobs[1].command == "/another/valid/cmd"


class TestSpecialKeywords:
    """Test cases for special keyword constants."""
    
    def test_special_keywords_exist(self):
        """Test that all expected special keywords are defined."""
        expected_keywords = [
            "@yearly", "@annually", "@monthly", "@weekly", 
            "@daily", "@midnight", "@hourly"
        ]
        
        for keyword in expected_keywords:
            assert keyword in SPECIAL_KEYWORDS
    
    def test_special_keyword_values(self):
        """Test that special keywords map to correct cron expressions."""
        assert SPECIAL_KEYWORDS["@yearly"] == "0 0 1 1 *"
        assert SPECIAL_KEYWORDS["@annually"] == "0 0 1 1 *"
        assert SPECIAL_KEYWORDS["@monthly"] == "0 0 1 * *"
        assert SPECIAL_KEYWORDS["@weekly"] == "0 0 * * 0"
        assert SPECIAL_KEYWORDS["@daily"] == "0 0 * * *"
        assert SPECIAL_KEYWORDS["@midnight"] == "0 0 * * *"
        assert SPECIAL_KEYWORDS["@hourly"] == "0 * * * *"


class TestIntegration:
    """Integration tests combining multiple components."""
    
    def test_mock_data_parsing(self):
        """Test that all mock data parses correctly."""
        jobs = load_user_crontab()  # Will use mock data on Windows
        
        # Verify all jobs are valid
        for job in jobs:
            assert job.is_valid, f"Invalid job: {job.raw_line}"
            assert job.command.startswith("/"), f"Command should be absolute path: {job.command}"
            assert job.source == "user"
    
    def test_round_trip_parsing(self):
        """Test that parsed jobs can be reconstructed."""
        test_lines = [
            "0 8 * * 1-5 /weekday/script.sh",
            "@daily /daily/task.sh",
            "*/15 * * * * /frequent/task.sh",
            "0 0 1 * * /monthly/report.sh"
        ]
        
        for line in test_lines:
            job = parse_cron_line(line)
            assert job is not None
            assert job.raw_line == line
            assert job.is_valid