#!/usr/bin/env python3
"""
Unit tests for file loading functionality in cron-query.
Tests the --file option and load_crontab_from_file function.
"""

import unittest
import tempfile
import os
from unittest.mock import patch, MagicMock
from io import StringIO
from contextlib import redirect_stdout

from cron_query.cron_loader import load_crontab_from_file, CronParseError
from cron_query.main import main, load_cron_jobs


class TestLoadCrontabFromFile(unittest.TestCase):
    """Test the load_crontab_from_file function."""
    
    def setUp(self):
        """Create temporary test files."""
        # Create a valid crontab file
        self.valid_crontab = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.txt')
        self.valid_crontab.write("""# Test crontab file
# This is a comment

# Daily backup
0 2 * * * /home/user/backup.sh

# Weekly report  
0 9 * * 1 /home/user/report.py

# Special keywords
@daily /home/user/daily.sh
@hourly /home/user/hourly.sh

# Complex schedules
30 8 * * 1-5 /home/user/weekday.sh
0 18 * * 6,0 /home/user/weekend.sh

# Empty line should be ignored

""")
        self.valid_crontab.close()
        
        # Create an invalid crontab file
        self.invalid_crontab = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.txt')
        self.invalid_crontab.write("""# This file has invalid entries
invalid line without enough fields
* * * /missing/field
@invalid_keyword /some/command
""")
        self.invalid_crontab.close()
        
        # Create an empty file
        self.empty_file = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.txt')
        self.empty_file.write("")
        self.empty_file.close()
    
    def tearDown(self):
        """Clean up temporary files."""
        try:
            os.unlink(self.valid_crontab.name)
            os.unlink(self.invalid_crontab.name)
            os.unlink(self.empty_file.name)
        except:
            pass
    
    def test_load_valid_crontab_file(self):
        """Test loading a valid crontab file."""
        jobs = load_crontab_from_file(self.valid_crontab.name)
        
        self.assertIsInstance(jobs, list)
        self.assertEqual(len(jobs), 6)  # Should load 6 valid jobs
        
        # Check first job
        self.assertEqual(jobs[0].cron_expression, "0 2 * * *")
        self.assertEqual(jobs[0].command, "/home/user/backup.sh")
        self.assertEqual(jobs[0].source, "system")
        
        # Check special keyword job
        daily_jobs = [job for job in jobs if "@daily" in job.raw_line]
        self.assertEqual(len(daily_jobs), 1)
        self.assertEqual(daily_jobs[0].cron_expression, "0 0 * * *")
    
    def test_load_nonexistent_file(self):
        """Test loading a file that doesn't exist."""
        with self.assertRaises(FileNotFoundError) as context:
            load_crontab_from_file("/nonexistent/file.txt")
        
        self.assertIn("Crontab file not found", str(context.exception))
    
    def test_load_directory_instead_of_file(self):
        """Test error when path points to directory."""
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaises(ValueError) as context:
                load_crontab_from_file(temp_dir)
            
            self.assertIn("Path is not a file", str(context.exception))
    
    def test_load_file_with_parse_errors(self):
        """Test loading file with some invalid entries."""
        # Should not raise exception, but should log warnings
        with patch('cron_query.cron_loader.logger') as mock_logger:
            jobs = load_crontab_from_file(self.invalid_crontab.name)
            
            # Should still return empty list (no valid jobs)
            self.assertEqual(len(jobs), 0)
            
            # Should have logged warnings
            self.assertTrue(mock_logger.warning.called)
    
    def test_load_empty_file(self):
        """Test loading an empty file."""
        jobs = load_crontab_from_file(self.empty_file.name)
        self.assertEqual(len(jobs), 0)
    
    def test_file_encoding_error(self):
        """Test handling of file encoding errors."""
        # Create file with invalid UTF-8
        binary_file = tempfile.NamedTemporaryFile(mode='wb', delete=False, suffix='.txt')
        binary_file.write(b'\xff\xfe\x00\x00invalid utf-8')
        binary_file.close()
        
        try:
            with self.assertRaises(CronParseError) as context:
                load_crontab_from_file(binary_file.name)
            
            self.assertIn("File encoding error", str(context.exception))
        finally:
            os.unlink(binary_file.name)
    
    def test_permission_error(self):
        """Test handling of permission errors."""
        with patch('builtins.open', side_effect=PermissionError("Access denied")):
            with self.assertRaises(PermissionError) as context:
                load_crontab_from_file(self.valid_crontab.name)
            
            self.assertIn("Permission denied reading file", str(context.exception))


class TestCLIFileOption(unittest.TestCase):
    """Test the CLI --file option integration."""
    
    def setUp(self):
        """Create test crontab file."""
        self.test_file = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.txt')
        self.test_file.write("""# Test crontab for CLI testing
0 8 * * 1 /home/user/monday-job.sh
30 17 * * 1-5 /home/user/weekday-job.sh
0 10 * * 6,0 /home/user/weekend-job.sh
""")
        self.test_file.close()
    
    def tearDown(self):
        """Clean up test file."""
        try:
            os.unlink(self.test_file.name)
        except:
            pass
    
    def test_cli_with_file_option(self):
        """Test CLI with --file option."""
        output = StringIO()
        with redirect_stdout(output):
            result = main(['--file', self.test_file.name, 'jobs on Monday'])
        
        self.assertEqual(result, 0)
        output_text = output.getvalue()
        self.assertIn('monday-job.sh', output_text)
        self.assertIn('weekday-job.sh', output_text)  # Runs on weekdays including Monday
    
    def test_cli_with_file_and_format_options(self):
        """Test CLI with both --file and --format options."""
        output = StringIO()
        with redirect_stdout(output):
            result = main(['--file', self.test_file.name, '--format', 'table', 'jobs on weekends'])
        
        self.assertEqual(result, 0)
        output_text = output.getvalue()
        self.assertIn('weekend-job.sh', output_text)
        self.assertIn('|', output_text)  # Should be table format
    
    def test_cli_with_nonexistent_file(self):
        """Test CLI with non-existent file."""
        output = StringIO()
        with redirect_stdout(output):
            result = main(['--file', '/nonexistent/file.txt', 'jobs on Monday'])
        
        self.assertEqual(result, 1)  # Should fail
        output_text = output.getvalue()
        self.assertIn('Error', output_text)
        self.assertIn('file not found', output_text)
    
    def test_file_takes_precedence_over_source(self):
        """Test that --file option takes precedence over --source."""
        # Even if we specify --source system, it should use the file
        with patch('cron_query.main.load_user_crontab') as mock_load_user:
            mock_load_user.return_value = []  # This shouldn't be called
            
            output = StringIO()
            with redirect_stdout(output):
                result = main(['--file', self.test_file.name, '--source', 'system', 'jobs on Monday'])
            
            self.assertEqual(result, 0)
            # Should not call load_user_crontab since file takes precedence
            mock_load_user.assert_not_called()


class TestLoadCronJobsFunction(unittest.TestCase):
    """Test the updated load_cron_jobs function."""
    
    def setUp(self):
        self.logger = MagicMock()
        
        # Create test file
        self.test_file = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.txt')
        self.test_file.write("0 8 * * * /test/command.sh\n")
        self.test_file.close()
    
    def tearDown(self):
        try:
            os.unlink(self.test_file.name)
        except:
            pass
    
    def test_load_from_file_when_file_specified(self):
        """Test that load_cron_jobs uses file when file_path is specified."""
        jobs = load_cron_jobs('user', self.test_file.name, self.logger)
        
        self.assertEqual(len(jobs), 1)
        self.assertEqual(jobs[0].source, 'system')
        self.assertEqual(jobs[0].command, '/test/command.sh')
    
    @patch('cron_query.main.load_user_crontab')
    def test_load_from_source_when_no_file(self, mock_load_user):
        """Test that load_cron_jobs uses source when no file specified."""
        mock_jobs = [MagicMock()]
        mock_load_user.return_value = mock_jobs
        
        jobs = load_cron_jobs('user', None, self.logger)
        
        self.assertEqual(jobs, mock_jobs)
        mock_load_user.assert_called_once()
    
    def test_unsupported_source_error(self):
        """Test error for unsupported sources."""
        with self.assertRaises(ValueError) as context:
            load_cron_jobs('invalid_source', None, self.logger)
        
        self.assertIn('Invalid source', str(context.exception))


class TestFileLoadingIntegration(unittest.TestCase):
    """Integration tests for file loading functionality."""
    
    def test_end_to_end_file_query(self):
        """Test complete end-to-end file loading and querying."""
        # Create test crontab with various job types
        test_content = """# Complex test crontab
# Daily jobs
0 2 * * * /home/user/daily-backup.sh
@daily /home/user/daily-cleanup.sh

# Weekly jobs  
0 9 * * 1 /home/user/monday-report.py
0 18 * * 5 /home/user/friday-deploy.sh

# Hourly job
0 * * * * /home/user/hourly-check.sh

# Weekend maintenance
0 10 * * 6,0 /home/user/weekend-maintenance.sh

# Weekday business hours
30 9 * * 1-5 /home/user/business-hours.sh
0 17 * * 1-5 /home/user/end-of-day.sh
"""
        
        # Use tempfile.mkdtemp for better Windows compatibility
        import shutil
        temp_dir = tempfile.mkdtemp()
        test_file = os.path.join(temp_dir, "test.crontab")
        
        try:
            # Write test content to file
            with open(test_file, 'w') as f:
                f.write(test_content)
            
            # Test various queries
            queries_and_expected = [
                ('jobs on Monday', ['monday-report.py', 'daily-backup.sh', 'daily-cleanup.sh', 'hourly-check.sh', 'business-hours.sh', 'end-of-day.sh']),
                ('jobs at 9 AM', ['monday-report.py', 'hourly-check.sh']),  # Only jobs at exactly 9:00 AM
                ('jobs on weekends', ['daily-backup.sh', 'daily-cleanup.sh', 'hourly-check.sh', 'weekend-maintenance.sh']),
                ('jobs on weekdays', ['daily-backup.sh', 'daily-cleanup.sh', 'monday-report.py', 'friday-deploy.sh', 'hourly-check.sh', 'business-hours.sh', 'end-of-day.sh'])
            ]
            
            for query, expected_commands in queries_and_expected:
                with self.subTest(query=query):
                    output = StringIO()
                    with redirect_stdout(output):
                        result = main(['--file', test_file, query])
                    
                    self.assertEqual(result, 0, f"Query '{query}' failed")
                    output_text = output.getvalue()
                    
                    for expected_cmd in expected_commands:
                        self.assertIn(expected_cmd, output_text, 
                                    f"Expected '{expected_cmd}' in results for query '{query}'")
        
        finally:
            # Clean up temp directory and all files in it
            shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == '__main__':
    unittest.main()