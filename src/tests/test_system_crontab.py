#!/usr/bin/env python3
"""
Tests for system crontab functionality in cron_loader.py
"""

import os
import pytest
from unittest.mock import patch, mock_open, MagicMock
from src.cron_query.cron_loader import (
    load_system_crontabs,
    parse_cron_line,
    CronParseError,
    _load_etc_crontab,
    _load_cron_d_directory,
    _load_cron_d_file,
    _get_mock_system_crontab_data
)


class TestParseSystemCronLine:
    """Test parsing system crontab format with user field."""

    def test_parse_system_crontab_format(self):
        """Test parsing system crontab with user field (7 fields)."""
        line = "0 2 * * * root /usr/local/bin/backup.sh"
        job = parse_cron_line(line, source="system")
        
        assert job is not None
        assert job.minute == "0"
        assert job.hour == "2"
        assert job.day_of_month == "*"
        assert job.month == "*"
        assert job.day_of_week == "*"
        assert job.user == "root"
        assert job.command == "/usr/local/bin/backup.sh"
        assert job.source == "system"
        assert job.raw_line == line

    def test_parse_system_crontab_with_command_args(self):
        """Test parsing system crontab with command arguments (8+ fields)."""
        line = "*/5 * * * * www-data /usr/bin/php /var/www/script.php --verbose --log=/tmp/log"
        job = parse_cron_line(line, source="system")
        
        assert job is not None
        assert job.user == "www-data"
        assert job.command == "/usr/bin/php /var/www/script.php --verbose --log=/tmp/log"

    def test_parse_standard_crontab_with_args_still_works(self):
        """Test that standard crontab format with command args still works."""
        line = "0 8 * * * /complex/script.sh --arg1 value1 --arg2 value2"
        job = parse_cron_line(line, source="user", user="testuser")
        
        assert job is not None
        assert job.user == "testuser"
        assert job.command == "/complex/script.sh --arg1 value1 --arg2 value2"
        assert job.source == "user"

    def test_parse_ambiguous_case_path_command(self):
        """Test parsing ambiguous case where 6th field could be user or command."""
        # Command starting with path - should be treated as standard format
        line = "0 8 * * * /usr/bin/some-command argument"
        job = parse_cron_line(line, source="system", user=None)
        
        assert job is not None
        assert job.user is None  # No user specified
        assert job.command == "/usr/bin/some-command argument"

    def test_parse_known_system_user(self):
        """Test parsing with known system user names."""
        line = "30 3 * * * nobody /usr/local/bin/cleanup.sh"
        job = parse_cron_line(line, source="system")
        
        assert job is not None
        assert job.user == "nobody"
        assert job.command == "/usr/local/bin/cleanup.sh"


class TestLoadEtcCrontab:
    """Test loading /etc/crontab file."""

    @patch('builtins.open', mock_open(read_data="""# System crontab
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin

# Run system maintenance
17 * * * * root cd / && run-parts --report /etc/cron.hourly
25 6 * * * root test -x /usr/sbin/anacron || cd / && run-parts --report /etc/cron.daily
"""))
    @patch('os.path.exists', return_value=True)
    def test_load_etc_crontab_success(self, mock_exists):
        """Test successfully loading /etc/crontab."""
        jobs = _load_etc_crontab()
        
        assert len(jobs) == 2
        assert jobs[0].user == "root"
        assert jobs[0].command == "cd / && run-parts --report /etc/cron.hourly"
        assert jobs[1].user == "root"
        assert jobs[1].command == "test -x /usr/sbin/anacron || cd / && run-parts --report /etc/cron.daily"

    @patch('os.path.exists', return_value=False)
    def test_load_etc_crontab_not_found(self, mock_exists):
        """Test handling when /etc/crontab doesn't exist."""
        jobs = _load_etc_crontab()
        assert jobs == []

    @patch('builtins.open', side_effect=PermissionError("Permission denied"))
    @patch('os.path.exists', return_value=True)
    def test_load_etc_crontab_permission_error(self, mock_exists, mock_open):
        """Test handling permission error when reading /etc/crontab."""
        with pytest.raises(PermissionError, match=r"Permission denied reading.*etc.crontab"):
            _load_etc_crontab()

    @patch('builtins.open', mock_open(read_data="""# System crontab with parse errors
SHELL=/bin/bash

# Valid job
0 2 * * * root /backup.sh

# Invalid job - too few fields
0 8 *

# Another valid job
30 6 * * 0 root /weekly.sh
"""))
    @patch('os.path.exists', return_value=True)
    def test_load_etc_crontab_with_parse_errors(self, mock_exists):
        """Test loading /etc/crontab with some parsing errors."""
        jobs = _load_etc_crontab()
        
        # Should get 2 valid jobs, skip the invalid one
        assert len(jobs) == 2
        assert jobs[0].command == "/backup.sh"
        assert jobs[1].command == "/weekly.sh"


class TestLoadCronDDirectory:
    """Test loading /etc/cron.d/ directory."""

    @patch('os.path.exists', return_value=True)
    @patch('os.path.isdir', return_value=True)
    @patch('glob.glob', return_value=['/etc/cron.d/file1', '/etc/cron.d/file2'])
    @patch('os.path.isfile', return_value=True)
    @patch('os.path.basename', side_effect=lambda x: x.split('/')[-1])
    def test_load_cron_d_directory_success(self, mock_basename, mock_isfile, mock_glob, mock_isdir, mock_exists):
        """Test successfully loading /etc/cron.d/ directory."""
        
        # Mock file contents
        file_contents = {
            '/etc/cron.d/file1': "0 1 * * * user1 /cmd1\n",
            '/etc/cron.d/file2': "0 2 * * * user2 /cmd2\n30 3 * * * user3 /cmd3\n"
        }
        
        def mock_open_func(file_path, *args, **kwargs):
            return mock_open(read_data=file_contents.get(file_path, ""))()
        
        with patch('builtins.open', side_effect=mock_open_func):
            jobs = _load_cron_d_directory()
        
        assert len(jobs) == 3
        assert jobs[0].user == "user1"
        assert jobs[0].command == "/cmd1"
        assert jobs[1].user == "user2" 
        assert jobs[1].command == "/cmd2"
        assert jobs[2].user == "user3"
        assert jobs[2].command == "/cmd3"

    @patch('os.path.exists', return_value=False)
    def test_load_cron_d_directory_not_found(self, mock_exists):
        """Test handling when /etc/cron.d/ doesn't exist."""
        jobs = _load_cron_d_directory()
        assert jobs == []

    @patch('os.path.exists', return_value=True)
    @patch('os.path.isdir', return_value=False)
    def test_load_cron_d_directory_not_dir(self, mock_isdir, mock_exists):
        """Test handling when /etc/cron.d/ exists but is not a directory."""
        jobs = _load_cron_d_directory()
        assert jobs == []

    @patch('os.path.exists', return_value=True)
    @patch('os.path.isdir', return_value=True)
    @patch('glob.glob', side_effect=PermissionError("Permission denied"))
    def test_load_cron_d_directory_permission_error(self, mock_glob, mock_isdir, mock_exists):
        """Test handling permission error when accessing /etc/cron.d/."""
        with pytest.raises(PermissionError, match=r"Permission denied accessing.*etc.cron.d"):
            _load_cron_d_directory()

    @patch('os.path.exists', return_value=True)
    @patch('os.path.isdir', return_value=True)
    @patch('glob.glob', return_value=['/etc/cron.d/.hidden', '/etc/cron.d/visible'])
    @patch('os.path.isfile', return_value=True)
    @patch('os.path.basename', side_effect=lambda x: x.split('/')[-1])
    def test_load_cron_d_directory_skips_hidden_files(self, mock_basename, mock_isfile, mock_glob, mock_isdir, mock_exists):
        """Test that hidden files are skipped in /etc/cron.d/."""
        
        with patch('builtins.open', mock_open(read_data="0 1 * * * user /cmd\n")):
            jobs = _load_cron_d_directory()
        
        # Should only process the visible file
        assert len(jobs) == 1


class TestLoadCronDFile:
    """Test loading individual /etc/cron.d/ files."""

    def test_load_cron_d_file_success(self):
        """Test successfully loading a single cron.d file."""
        file_content = """# Cron.d file
MAILTO=admin@example.com

# System jobs
0 1 * * * user1 /path/to/command1
30 2 * * 0 user2 /path/to/command2 --arg value
"""
        
        with patch('builtins.open', mock_open(read_data=file_content)):
            jobs = _load_cron_d_file('/etc/cron.d/test')
        
        assert len(jobs) == 2
        assert jobs[0].user == "user1"
        assert jobs[0].command == "/path/to/command1"
        assert jobs[1].user == "user2"
        assert jobs[1].command == "/path/to/command2 --arg value"

    def test_load_cron_d_file_permission_error(self):
        """Test handling permission error when reading cron.d file."""
        with patch('builtins.open', side_effect=PermissionError("Permission denied")):
            with pytest.raises(PermissionError, match="Permission denied reading /etc/cron.d/test"):
                _load_cron_d_file('/etc/cron.d/test')

    def test_load_cron_d_file_encoding_error(self):
        """Test handling encoding error when reading cron.d file."""
        with patch('builtins.open', side_effect=UnicodeDecodeError('utf-8', b'', 0, 1, 'invalid')):
            with pytest.raises(CronParseError, match="File encoding error"):
                _load_cron_d_file('/etc/cron.d/test')


class TestLoadSystemCrontabs:
    """Test the main load_system_crontabs function."""

    @patch('src.cron_query.cron_loader.sys.platform', 'win32')
    def test_load_system_crontabs_windows_mock(self):
        """Test loading system crontabs on Windows (uses mock data)."""
        jobs = load_system_crontabs()
        
        assert isinstance(jobs, list)
        assert len(jobs) > 0
        
        # Check that we got some system-style jobs
        system_jobs = [job for job in jobs if job.source == "system"]
        assert len(system_jobs) > 0
        
        # Check for some expected mock commands
        commands = [job.command for job in jobs]
        assert any("run-parts" in cmd for cmd in commands)
        assert any("backup" in cmd for cmd in commands)

    @patch('src.cron_query.cron_loader.sys.platform', 'linux')
    def test_load_system_crontabs_linux_success(self):
        """Test loading system crontabs on Linux successfully."""
        
        # Mock successful loading from both sources
        with patch('src.cron_query.cron_loader._load_etc_crontab') as mock_etc, \
             patch('src.cron_query.cron_loader._load_cron_d_directory') as mock_cron_d:
            
            # Create mock jobs
            from src.cron_query.cron_loader import CronJob
            etc_jobs = [CronJob("0", "2", "*", "*", "*", "/etc/job", "0 2 * * * root /etc/job", "root", "system")]
            cron_d_jobs = [CronJob("0", "3", "*", "*", "*", "/cron.d/job", "0 3 * * * user /cron.d/job", "user", "system")]
            
            mock_etc.return_value = etc_jobs
            mock_cron_d.return_value = cron_d_jobs
            
            jobs = load_system_crontabs()
            
            assert len(jobs) == 2
            assert jobs[0].command == "/etc/job"
            assert jobs[1].command == "/cron.d/job"

    @patch('src.cron_query.cron_loader.sys.platform', 'linux')
    def test_load_system_crontabs_partial_failure(self):
        """Test loading system crontabs with partial failures."""
        
        with patch('src.cron_query.cron_loader._load_etc_crontab') as mock_etc, \
             patch('src.cron_query.cron_loader._load_cron_d_directory') as mock_cron_d:
            
            # /etc/crontab fails, but /etc/cron.d/ succeeds
            mock_etc.side_effect = PermissionError("Cannot read /etc/crontab")
            
            from src.cron_query.cron_loader import CronJob
            cron_d_jobs = [CronJob("0", "3", "*", "*", "*", "/cron.d/job", "0 3 * * * user /cron.d/job", "user", "system")]
            mock_cron_d.return_value = cron_d_jobs
            
            jobs = load_system_crontabs()
            
            # Should still get the cron.d jobs
            assert len(jobs) == 1
            assert jobs[0].command == "/cron.d/job"

    @patch('src.cron_query.cron_loader.sys.platform', 'linux')
    def test_load_system_crontabs_total_failure(self):
        """Test loading system crontabs when both sources fail."""
        
        with patch('src.cron_query.cron_loader._load_etc_crontab') as mock_etc, \
             patch('src.cron_query.cron_loader._load_cron_d_directory') as mock_cron_d:
            
            mock_etc.side_effect = PermissionError("Cannot read /etc/crontab")
            mock_cron_d.side_effect = PermissionError("Cannot read /etc/cron.d")
            
            with pytest.raises(PermissionError, match="Could not load any system crontabs"):
                load_system_crontabs()


class TestMockSystemCrontabData:
    """Test mock system crontab data generation."""

    def test_mock_system_crontab_data(self):
        """Test that mock system data is generated correctly."""
        jobs = _get_mock_system_crontab_data()
        
        assert isinstance(jobs, list)
        assert len(jobs) > 0
        
        # All jobs should be marked as system source
        for job in jobs:
            assert job.source == "system"
        
        # Should have a mix of users
        users = {job.user for job in jobs if job.user}
        assert "root" in users
        assert len(users) > 1  # Should have multiple different users

    def test_mock_system_data_parsing(self):
        """Test that mock system data can be properly parsed."""
        jobs = _get_mock_system_crontab_data()
        
        # All jobs should be valid
        for job in jobs:
            assert job.is_valid
            assert job.cron_expression is not None
            assert job.command is not None


class TestIntegrationSystemCrontab:
    """Integration tests for system crontab functionality."""

    @patch('src.cron_query.cron_loader.sys.platform', 'win32')
    def test_system_crontab_integration_with_user_query(self):
        """Test that system crontab jobs work with query analysis."""
        from src.cron_query.schedule_analyzer import find_matching_jobs
        from src.cron_query.query_parser import parse_query
        
        # Load system crontabs
        jobs = load_system_crontabs()
        assert len(jobs) > 0
        
        # Parse a query
        criteria = parse_query("which jobs run at 2 AM")
        
        # Find matching jobs
        matches = find_matching_jobs(jobs, criteria)
        
        # Should find some matches
        assert len(matches) > 0
        
        # All matches should be system jobs
        for job in matches:
            assert job.source == "system"

    @patch('src.cron_query.cron_loader.sys.platform', 'win32') 
    def test_system_crontab_users_preserved(self):
        """Test that user information is preserved in system crontabs."""
        jobs = load_system_crontabs()
        
        # Should have jobs with different users
        users_with_jobs = [job.user for job in jobs if job.user]
        assert len(users_with_jobs) > 0
        
        # Check for expected system users
        user_set = set(users_with_jobs)
        expected_users = {"root", "backup", "monitor", "admin"}
        assert len(user_set.intersection(expected_users)) > 0