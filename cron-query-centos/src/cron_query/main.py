#!/usr/bin/env python3
"""
Main CLI entry point for cron-query tool.
"""

import argparse
import logging
import sys
import time
from typing import Optional, List

from . import __version__, __description__
from .cron_loader import load_user_crontab, load_crontab_from_file, CronJob
from .query_parser import parse_query, QueryCriteria, format_criteria_description
from .schedule_analyzer import find_matching_jobs
from .output_formatter import format_query_results, format_error_message, validate_output_format


def setup_logging(verbose: bool = False) -> None:
    """Set up logging configuration."""
    if verbose:
        level = logging.DEBUG
        logging.basicConfig(
            level=level,
            format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )
    else:
        # In normal mode, only show warnings and errors
        level = logging.WARNING
        logging.basicConfig(
            level=level,
            format='%(levelname)s: %(message)s'
        )


def create_parser() -> argparse.ArgumentParser:
    """Create and configure the argument parser."""
    parser = argparse.ArgumentParser(
        prog='cron-query',
        description=__description__,
        epilog="""
Examples:
  cron-query "which jobs run on Saturday"
  cron-query "which jobs run at 8 AM"
  cron-query "which jobs run this Saturday"
  cron-query --format json "which jobs run on weekdays"
        """,
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    
    parser.add_argument(
        'query',
        help='Natural language query about cron job schedules'
    )
    
    parser.add_argument(
        '--format',
        choices=['list', 'table', 'json'],
        default='list',
        help='Output format (default: list)'
    )
    
    parser.add_argument(
        '--source',
        choices=['user', 'system', 'all'],
        default='user',
        help='Crontab source to query (default: user)'
    )
    
    parser.add_argument(
        '--file',
        metavar='PATH',
        help='Path to crontab file to analyze instead of system crontab'
    )
    
    parser.add_argument(
        '--verbose', '-v',
        action='store_true',
        help='Enable verbose logging'
    )
    
    parser.add_argument(
        '--version',
        action='version',
        version=f'%(prog)s {__version__}'
    )
    
    return parser


def load_cron_jobs(source: str, file_path: Optional[str], logger: logging.Logger) -> List[CronJob]:
    """
    Load cron jobs from the specified source or file.
    
    Args:
        source: Crontab source (user, system, all)
        file_path: Path to crontab file (takes precedence over source)
        logger: Logger instance
        
    Returns:
        List of CronJob objects
        
    Raises:
        ValueError: If source is not supported or file not found
        RuntimeError: If loading fails
    """
    if file_path:
        logger.info(f"Loading cron jobs from file: {file_path}")
        jobs = load_crontab_from_file(file_path)
        logger.info(f"Loaded {len(jobs)} cron jobs from file")
        return jobs
    
    if source in ['system', 'all']:
        raise ValueError(f"Source '{source}' is not yet supported. Currently only 'user' is supported.")
    
    logger.info(f"Loading cron jobs from {source} crontab")
    jobs = load_user_crontab()
    logger.info(f"Loaded {len(jobs)} cron jobs")
    
    return jobs


def process_query(query: str, output_format: str, source: str, file_path: Optional[str] = None) -> int:
    """
    Process the user's query and return results.
    
    Args:
        query: Natural language query string
        output_format: Output format (list, table, json)
        source: Crontab source (user, system, all)
        file_path: Path to crontab file (optional, takes precedence over source)
        
    Returns:
        Exit code (0 for success, non-zero for error)
    """
    logger = logging.getLogger(__name__)
    start_time = time.time()
    
    logger.info(f"Processing query: '{query}'")
    logger.info(f"Output format: {output_format}")
    logger.info(f"Source: {source}")
    
    try:
        # Validate output format
        if not validate_output_format(output_format):
            error_msg = format_error_message(
                Exception(f"Invalid output format '{output_format}'. Supported formats: list, table, json"),
                query=query
            )
            print(error_msg)
            return 1
        
        # Load cron jobs
        try:
            jobs = load_cron_jobs(source, file_path, logger)
        except ValueError as e:
            error_msg = format_error_message(e, query=query)
            print(error_msg)
            return 1
        except Exception as e:
            error_msg = format_error_message(
                Exception(f"Failed to load cron jobs: {e}"),
                query=query
            )
            print(error_msg)
            return 1
        
        # Parse the query
        try:
            criteria = parse_query(query)
            logger.info(f"Parsed query criteria: {criteria}")
        except ValueError as e:
            error_msg = format_error_message(
                Exception(f"Could not understand query: {e}"),
                query=query
            )
            print(error_msg)
            return 1
        except Exception as e:
            error_msg = format_error_message(
                Exception(f"Error parsing query: {e}"),
                query=query
            )
            print(error_msg)
            return 1
        
        # Find matching jobs
        try:
            matching_jobs = find_matching_jobs(jobs, criteria)
            logger.info(f"Found {len(matching_jobs)} matching jobs")
        except Exception as e:
            error_msg = format_error_message(
                Exception(f"Error analyzing schedules: {e}"),
                query=query
            )
            print(error_msg)
            return 1
        
        # Format and display results
        try:
            execution_time = time.time() - start_time
            output = format_query_results(
                matching_jobs, 
                criteria, 
                output_format=output_format,
                show_next_runs=True
            )
            print(output)
        except Exception as e:
            error_msg = format_error_message(
                Exception(f"Error formatting results: {e}"),
                query=query
            )
            print(error_msg)
            return 1
        
        logger.debug(f"Query processed successfully in {execution_time:.3f} seconds")
        return 0
        
    except Exception as e:
        # Catch-all for any unexpected errors
        execution_time = time.time() - start_time
        error_msg = format_error_message(
            Exception(f"Unexpected error: {e}"),
            query=query
        )
        print(error_msg)
        logger.exception("Unexpected error during query processing")
        return 1


def main(argv: Optional[list] = None) -> int:
    """
    Main entry point for the CLI application.
    
    Args:
        argv: Command line arguments (defaults to sys.argv)
        
    Returns:
        Exit code
    """
    parser = create_parser()
    args = parser.parse_args(argv)
    
    # Set up logging
    setup_logging(args.verbose)
    logger = logging.getLogger(__name__)
    
    logger.debug("Starting cron-query application")
    logger.debug(f"Arguments: {args}")
    
    try:
        return process_query(args.query, args.format, args.source, args.file)
    except KeyboardInterrupt:
        logger.info("Interrupted by user")
        return 130
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        if args.verbose:
            logger.exception("Full traceback:")
        return 1


if __name__ == '__main__':
    sys.exit(main())