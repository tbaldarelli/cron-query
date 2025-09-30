#!/usr/bin/env python3
"""
Integration tests for the main CLI functionality.
Tests the complete flow from argument parsing to output formatting.
"""

import unittest
from unittest.mock import patch, MagicMock
import sys
import io
import json
from contextlib import redirect_stdout, redirect_stderr

from cron_query.main import main, create_parser, process_query, load_cron_jobs
from cron_query.cron_loader import CronJob
from cron_query.query_parser import QueryCriteria, QueryType


class TestCLIArgumentParsing(unittest.TestCase):
    """Test command-line argument parsing."""
    
    def setUp(self):
        self.parser = create_parser()
    
    def test_basic_query_parsing(self):
        """Test parsing basic query arguments."""
        args = self.parser.parse_args(['jobs on Monday'])
        self.assertEqual(args.query, 'jobs on Monday')
        self.assertEqual(args.format, 'list')
        self.assertEqual(args.source, 'user')
        self.assertFalse(args.verbose)
    
    def test_format_options(self):
        """Test different output format options."""
        # Test list format (default)
        args = self.parser.parse_args(['test query'])
        self.assertEqual(args.format, 'list')
        
        # Test table format
        args = self.parser.parse_args(['--format', 'table', 'test query'])
        self.assertEqual(args.format, 'table')
        
        # Test JSON format
        args = self.parser.parse_args(['--format', 'json', 'test query'])
        self.assertEqual(args.format, 'json')
    
    def test_source_options(self):
        """Test different source options."""
        # Test user source (default)
        args = self.parser.parse_args(['test query'])
        self.assertEqual(args.source, 'user')
        
        # Test system source
        args = self.parser.parse_args(['--source', 'system', 'test query'])
        self.assertEqual(args.source, 'system')
        
        # Test all source
        args = self.parser.parse_args(['--source', 'all', 'test query'])
        self.assertEqual(args.source, 'all')
    
    def test_verbose_flag(self):
        """Test verbose flag options."""
        # Test short form
        args = self.parser.parse_args(['-v', 'test query'])
        self.assertTrue(args.verbose)
        
        # Test long form
        args = self.parser.parse_args(['--verbose', 'test query'])
        self.assertTrue(args.verbose)
    
    def test_invalid_format(self):
        """Test invalid format argument."""
        with self.assertRaises(SystemExit):
            self.parser.parse_args(['--format', 'invalid', 'test query'])
    
    def test_invalid_source(self):
        """Test invalid source argument."""
        with self.assertRaises(SystemExit):
            self.parser.parse_args(['--source', 'invalid', 'test query'])
    
    def test_missing_query(self):
        """Test missing query argument."""
        with self.assertRaises(SystemExit):
            main(['--format', 'table'])
    
    def test_complex_argument_combination(self):
        """Test complex argument combinations."""
        args = self.parser.parse_args([
            '--format', 'json',
            '--source', 'system', 
            '--verbose',
            'jobs that run on weekdays at 8 AM'
        ])
        self.assertEqual(args.query, 'jobs that run on weekdays at 8 AM')
        self.assertEqual(args.format, 'json')
        self.assertEqual(args.source, 'system')
        self.assertTrue(args.verbose)


class TestLoadCronJobs(unittest.TestCase):
    """Test cron job loading functionality."""
    
    def setUp(self):
        self.logger = MagicMock()
    
    @patch('cron_query.main.load_user_crontab')
    def test_load_user_jobs_success(self, mock_load):
        """Test successful loading of user cron jobs."""
        mock_jobs = [
            CronJob("0", "8", "*", "*", "1", "echo 'Monday job'", "0 8 * * 1 echo 'Monday job'", "user", "user"),
            CronJob("30", "17", "*", "*", "*", "backup.sh", "30 17 * * * backup.sh", "user", "user")
        ]
        mock_load.return_value = mock_jobs
        
        jobs = load_cron_jobs('user', None, self.logger)
        
        self.assertEqual(len(jobs), 2)
        self.assertEqual(jobs[0].command, "echo 'Monday job'")
        mock_load.assert_called_once()
        self.logger.info.assert_called()
    
    def test_unsupported_source(self):
        """Test error handling for unsupported sources."""
        with self.assertRaises(ValueError) as context:
            load_cron_jobs('invalid_source', None, self.logger)
        
        self.assertIn("Invalid source", str(context.exception))
    
    @patch('cron_query.main.load_user_crontab')
    def test_load_jobs_exception(self, mock_load):
        """Test exception handling during job loading."""
        mock_load.side_effect = Exception("Crontab not found")
        
        # Should not raise - error handling is in process_query
        with self.assertRaises(Exception):
            load_cron_jobs('user', None, self.logger)


class TestProcessQuery(unittest.TestCase):
    """Test the complete query processing workflow."""
    
    def setUp(self):
        self.sample_jobs = [
            CronJob("0", "8", "*", "*", "1", "weekly_backup.sh", "0 8 * * 1 weekly_backup.sh", "user", "user"),
            CronJob("30", "17", "*", "*", "1-5", "daily_cleanup.sh", "30 17 * * 1-5 daily_cleanup.sh", "user", "user"),
            CronJob("0", "0", "1", "*", "*", "monthly_report.py", "0 0 1 * * monthly_report.py", "user", "user"),
            CronJob("*/15", "*", "*", "*", "*", "health_check.sh", "*/15 * * * * health_check.sh", "user", "user")
        ]
    
    @patch('cron_query.main.load_cron_jobs')
    @patch('cron_query.main.parse_query')
    @patch('cron_query.main.find_matching_jobs')
    def test_successful_query_processing(self, mock_find, mock_parse, mock_load):
        """Test successful end-to-end query processing."""
        # Mock setup
        mock_load.return_value = self.sample_jobs
        mock_criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query='jobs on Monday',
            days_of_week={1}  # Monday
        )
        mock_parse.return_value = mock_criteria
        mock_find.return_value = [self.sample_jobs[0]]  # Monday job
        
        # Capture output
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('jobs on Monday', 'list', 'user')
        
        self.assertEqual(result, 0)
        output_text = output.getvalue()
        self.assertIn('weekly_backup.sh', output_text)
        self.assertIn('Monday', output_text)
    
    @patch('cron_query.main.load_cron_jobs')
    @patch('cron_query.main.parse_query')
    @patch('cron_query.main.find_matching_jobs')
    def test_json_output_format(self, mock_find, mock_parse, mock_load):
        """Test JSON output formatting."""
        # Mock setup
        mock_load.return_value = self.sample_jobs
        mock_criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query='jobs on Monday',
            days_of_week={1}
        )
        mock_parse.return_value = mock_criteria
        mock_find.return_value = [self.sample_jobs[0]]
        
        # Capture output
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('jobs on Monday', 'json', 'user')
        
        self.assertEqual(result, 0)
        output_text = output.getvalue()
        
        # Verify it's valid JSON
        lines = output_text.strip().split('\n')
        json_line = None
        for line in lines:
            if line.startswith('{'):
                json_line = line
                break
        
        self.assertIsNotNone(json_line)
        parsed_json = json.loads(json_line)
        self.assertIn('query', parsed_json)
        self.assertIn('results', parsed_json)
    
    @patch('cron_query.main.load_cron_jobs')
    def test_invalid_output_format(self, mock_load):
        """Test handling of invalid output format."""
        mock_load.return_value = self.sample_jobs
        
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('test query', 'invalid', 'user')
        
        self.assertEqual(result, 1)
        output_text = output.getvalue()
        self.assertIn('Invalid output format', output_text)
    
    @patch('cron_query.main.load_cron_jobs')
    def test_unsupported_source_error(self, mock_load):
        """Test error handling for unsupported sources."""
        mock_load.side_effect = ValueError("Source 'system' is not yet supported")
        
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('test query', 'list', 'system')
        
        self.assertEqual(result, 1)
        output_text = output.getvalue()
        self.assertIn('not yet supported', output_text)
    
    @patch('cron_query.main.load_cron_jobs')
    @patch('cron_query.main.parse_query')
    def test_query_parsing_error(self, mock_parse, mock_load):
        """Test handling of query parsing errors."""
        mock_load.return_value = self.sample_jobs
        mock_parse.side_effect = ValueError("Could not parse query")
        
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('invalid query', 'list', 'user')
        
        self.assertEqual(result, 1)
        output_text = output.getvalue()
        self.assertIn('Could not understand query', output_text)
        self.assertIn('Examples of supported queries', output_text)  # Should include suggestions
    
    @patch('cron_query.main.load_cron_jobs')
    @patch('cron_query.main.parse_query')
    @patch('cron_query.main.find_matching_jobs')
    def test_empty_results(self, mock_find, mock_parse, mock_load):
        """Test handling of empty search results."""
        mock_load.return_value = self.sample_jobs
        mock_criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query='jobs on Saturday',
            days_of_week={6}  # Saturday
        )
        mock_parse.return_value = mock_criteria
        mock_find.return_value = []  # No matching jobs
        
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('jobs on Saturday', 'list', 'user')
        
        self.assertEqual(result, 0)  # Empty results are not an error
        output_text = output.getvalue()
        self.assertIn('No jobs found', output_text)
    
    @patch('cron_query.main.load_cron_jobs')
    @patch('cron_query.main.parse_query')
    @patch('cron_query.main.find_matching_jobs')
    def test_schedule_analyzer_error(self, mock_find, mock_parse, mock_load):
        """Test handling of schedule analyzer errors."""
        mock_load.return_value = self.sample_jobs
        mock_criteria = QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query='jobs on Monday',
            days_of_week={1}
        )
        mock_parse.return_value = mock_criteria
        mock_find.side_effect = Exception("Schedule analysis failed")
        
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('jobs on Monday', 'list', 'user')
        
        self.assertEqual(result, 1)
        output_text = output.getvalue()
        self.assertIn('Error analyzing schedules', output_text)


class TestMainEntryPoint(unittest.TestCase):
    """Test the main() entry point function."""
    
    @patch('cron_query.main.process_query')
    def test_main_success(self, mock_process):
        """Test successful main execution."""
        mock_process.return_value = 0
        
        result = main(['test query'])
        self.assertEqual(result, 0)
        mock_process.assert_called_once()
    
    @patch('cron_query.main.process_query')
    def test_main_with_arguments(self, mock_process):
        """Test main with various argument combinations."""
        mock_process.return_value = 0
        
        result = main(['--format', 'json', '--verbose', 'test query'])
        self.assertEqual(result, 0)
        # Just check that it was called once with the right query parameter
        mock_process.assert_called_once()
        args, kwargs = mock_process.call_args
        self.assertEqual(kwargs['query'], 'test query')
        self.assertEqual(kwargs['output_format'], 'json')
        self.assertEqual(kwargs['source'], 'user')
        self.assertTrue(kwargs['verbose'])
    
    @patch('cron_query.main.process_query')
    def test_keyboard_interrupt(self, mock_process):
        """Test handling of keyboard interrupt."""
        mock_process.side_effect = KeyboardInterrupt()
        
        # Suppress log output during test
        with patch('cron_query.main.setup_logging'):
            result = main(['test query'])
        
        self.assertEqual(result, 130)
    
    @patch('cron_query.main.process_query')
    def test_unexpected_exception(self, mock_process):
        """Test handling of unexpected exceptions."""
        mock_process.side_effect = RuntimeError("Unexpected error")
        
        # Suppress log output during test
        with patch('cron_query.main.setup_logging'):
            result = main(['test query'])
        
        self.assertEqual(result, 1)
    
    def test_invalid_arguments(self):
        """Test handling of invalid command-line arguments."""
        # Test with invalid argument - should exit with code 2
        with self.assertRaises(SystemExit) as context:
            main(['--invalid-option'])
        
        # argparse exits with code 2 for invalid arguments
        self.assertEqual(context.exception.code, 2)


class TestErrorFormatting(unittest.TestCase):
    """Test error message formatting in various scenarios."""
    
    @patch('cron_query.main.load_cron_jobs')
    def test_cron_loading_error_message(self, mock_load):
        """Test error message formatting for cron loading failures."""
        mock_load.side_effect = Exception("Permission denied")
        
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('test query', 'list', 'user')
        
        self.assertEqual(result, 1)
        output_text = output.getvalue()
        self.assertIn('Failed to load cron jobs', output_text)
        self.assertIn('Permission denied', output_text)
    
    @patch('cron_query.main.load_cron_jobs')
    @patch('cron_query.main.parse_query')
    @patch('cron_query.main.find_matching_jobs')
    @patch('cron_query.main.format_query_results')
    def test_output_formatting_error(self, mock_format, mock_find, mock_parse, mock_load):
        """Test error handling during output formatting."""
        mock_load.return_value = []
        mock_parse.return_value = QueryCriteria(query_type=QueryType.DAY_BASED, raw_query='test query', days_of_week={1})
        mock_find.return_value = []
        mock_format.side_effect = Exception("Formatting failed")
        
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('test query', 'list', 'user')
        
        self.assertEqual(result, 1)
        output_text = output.getvalue()
        self.assertIn('Error formatting results', output_text)


class TestIntegrationScenarios(unittest.TestCase):
    """Test realistic integration scenarios."""
    
    @patch('cron_query.main.load_user_crontab')
    def test_realistic_monday_query(self, mock_load):
        """Test a realistic Monday query with actual cron jobs."""
        mock_load.return_value = [
            CronJob("0", "8", "*", "*", "1", "weekly_backup.sh", "0 8 * * 1 weekly_backup.sh", "user", "user"),
            CronJob("30", "17", "*", "*", "1-5", "daily_cleanup.sh", "30 17 * * 1-5 daily_cleanup.sh", "user", "user"),
            CronJob("0", "0", "1", "*", "*", "monthly_report.py", "0 0 1 * * monthly_report.py", "user", "user")
        ]
        
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('jobs on Monday', 'list', 'user')
        
        self.assertEqual(result, 0)
        output_text = output.getvalue()
        self.assertIn('weekly_backup.sh', output_text)
        self.assertIn('daily_cleanup.sh', output_text)
        self.assertNotIn('monthly_report.py', output_text)
    
    @patch('cron_query.main.load_user_crontab')
    def test_realistic_time_query(self, mock_load):
        """Test a realistic time-based query."""
        mock_load.return_value = [
            CronJob("0", "8", "*", "*", "*", "morning_job.sh", "0 8 * * * morning_job.sh", "user", "user"),
            CronJob("30", "8", "*", "*", "*", "another_morning_job.sh", "30 8 * * * another_morning_job.sh", "user", "user"),
            CronJob("0", "17", "*", "*", "*", "evening_job.sh", "0 17 * * * evening_job.sh", "user", "user")
        ]
        
        output = io.StringIO()
        with redirect_stdout(output):
            result = process_query('jobs at 8 AM', 'table', 'user')
        
        self.assertEqual(result, 0)
        output_text = output.getvalue()
        self.assertIn('morning_job.sh', output_text)
        self.assertIn('another_morning_job.sh', output_text)
        self.assertNotIn('evening_job.sh', output_text)
        # Table format should have headers
        self.assertIn('Command', output_text)
        self.assertIn('Schedule', output_text)


if __name__ == '__main__':
    unittest.main()
