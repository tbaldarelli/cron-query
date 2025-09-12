#!/usr/bin/env python3
"""
Main CLI entry point for cron-query tool.
"""

import argparse
import logging
import sys
from typing import Optional

from . import __version__, __description__


def setup_logging(verbose: bool = False) -> None:
    """Set up logging configuration."""
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
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


def process_query(query: str, output_format: str, source: str) -> int:
    """
    Process the user's query and return results.
    
    Args:
        query: Natural language query string
        output_format: Output format (list, table, json)
        source: Crontab source (user, system, all)
        
    Returns:
        Exit code (0 for success, non-zero for error)
    """
    logger = logging.getLogger(__name__)
    
    logger.info(f"Processing query: '{query}'")
    logger.info(f"Output format: {output_format}")
    logger.info(f"Source: {source}")
    
    # TODO: Implement actual query processing
    # This is a placeholder for the MVP implementation
    print(f"🔍 Query: {query}")
    print(f"📊 Format: {output_format}")
    print(f"📂 Source: {source}")
    print()
    print("⚠️  This is a placeholder - actual implementation coming soon!")
    print("    The query processing logic will be implemented in subsequent tasks.")
    
    return 0


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
        return process_query(args.query, args.format, args.source)
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