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
from .cron_loader import load_user_crontab, load_crontab_from_file, load_system_crontabs, CronJob
from .query_parser import parse_query, QueryCriteria, format_criteria_description
from .schedule_analyzer import find_matching_jobs
from .output_formatter import (
    format_query_results, 
    format_error_message, 
    validate_output_format,
    get_supported_formats,
    get_predefined_templates,
    get_template_help,
    is_color_supported
)


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
  Basic queries:
    cron-query "which jobs run on Saturday"
    cron-query "which jobs run at 8 AM"
    cron-query "which jobs run on weekdays"
  
  Relative date queries:
    cron-query "which jobs run this Saturday"
    cron-query "which jobs run next Monday"
    cron-query "which jobs run coming weekend"
  
  Time range queries:
    cron-query "which jobs run after 10 AM"
    cron-query "which jobs run before 5 PM"
    cron-query "which jobs run between 9 AM and 5 PM"
  
  Combined queries:
    cron-query "which jobs run this Saturday after 10 AM"
    cron-query "which jobs run weekends before 5 PM"
    cron-query "which jobs run Monday between 9 AM and 5 PM"
  
  File analysis:
    cron-query --file /path/to/crontab "which jobs run on Saturday"
  
  Output formats:
    cron-query --format json "which jobs run today"
    cron-query --format table "which jobs run after 6 PM"
        """,
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    
    parser.add_argument(
        'query',
        nargs='?',
        help='Natural language query about cron job schedules. '
             'Supports basic queries ("Saturday", "8 AM"), '
             'relative dates ("this Saturday", "next Monday"), '
             'time ranges ("after 10 AM", "between 9 AM and 5 PM"), '
             'and combined queries ("this Saturday after 10 AM").'
    )
    
    # Get supported formats dynamically
    supported_formats = get_supported_formats()
    parser.add_argument(
        '--format',
        choices=supported_formats,
        default='list',
        help=f'Output format (default: list). Available: {", ".join(supported_formats)}'
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
        help='Enable verbose logging and detailed cron parsing information'
    )
    
    # Color options
    parser.add_argument(
        '--no-color',
        action='store_true',
        help='Disable colored output'
    )
    
    # Template options
    parser.add_argument(
        '--template',
        help='Custom output template or predefined template name (compact, detailed, summary, verbose)'
    )
    
    parser.add_argument(
        '--list-templates',
        action='store_true',
        help='List available predefined templates'
    )
    
    parser.add_argument(
        '--template-help',
        action='store_true',
        help='Show template help with available variables'
    )
    
    # Pagination options
    parser.add_argument(
        '--page-size',
        type=int,
        default=20,
        help='Number of results per page (default: 20)'
    )
    
    parser.add_argument(
        '--page',
        type=int,
        default=1,
        help='Page number to display (default: 1)'
    )
    
    parser.add_argument(
        '--no-pager',
        action='store_true',
        help='Disable pagination'
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
    
    # Handle different source types
    if source == 'user':
        logger.info(f"Loading cron jobs from {source} crontab")
        jobs = load_user_crontab()
        logger.info(f"Loaded {len(jobs)} cron jobs from user crontab")
        return jobs
    elif source == 'system':
        logger.info(f"Loading cron jobs from {source} crontab")
        jobs = load_system_crontabs()
        logger.info(f"Loaded {len(jobs)} cron jobs from system crontabs")
        return jobs
    elif source == 'all':
        logger.info(f"Loading cron jobs from all sources")
        jobs = []
        
        # Load user crontab
        try:
            user_jobs = load_user_crontab()
            jobs.extend(user_jobs)
            logger.info(f"Loaded {len(user_jobs)} cron jobs from user crontab")
        except Exception as e:
            logger.warning(f"Failed to load user crontab: {e}")
        
        # Load system crontabs
        try:
            system_jobs = load_system_crontabs()
            jobs.extend(system_jobs)
            logger.info(f"Loaded {len(system_jobs)} cron jobs from system crontabs")
        except Exception as e:
            logger.warning(f"Failed to load system crontabs: {e}")
        
        logger.info(f"Loaded {len(jobs)} total cron jobs from all sources")
        return jobs
    else:
        raise ValueError(f"Invalid source '{source}'. Supported sources: user, system, all")


def handle_template_info_requests(args) -> Optional[int]:
    """
    Handle --list-templates and --template-help requests.
    
    Args:
        args: Parsed command line arguments
        
    Returns:
        Exit code if handled, None if not handled
    """
    if args.list_templates:
        print("Available predefined templates:")
        print()
        templates = get_predefined_templates()
        for name, template in templates.items():
            print(f"  {name:<10} - {template}")
        print()
        print("Use --template-help for more details on template variables.")
        return 0
    
    if args.template_help:
        print(get_template_help())
        return 0
    
    return None


def process_query(
    query: str, 
    output_format: str, 
    source: str, 
    file_path: Optional[str] = None,
    use_colors: bool = True,
    page_size: int = 20,
    page: int = 1,
    use_pager: bool = True,
    template: Optional[str] = None,
    verbose: bool = False
) -> int:
    """
    Process the user's query and return results.
    
    Args:
        query: Natural language query string
        output_format: Output format (list, table, json, csv, yaml)
        source: Crontab source (user, system, all)
        file_path: Path to crontab file (optional, takes precedence over source)
        use_colors: Whether to use colored output
        page_size: Number of items per page
        page: Current page number
        use_pager: Whether to paginate output
        template: Custom template string or predefined template name
        verbose: Whether to show verbose information
        
    Returns:
        Exit code (0 for success, non-zero for error)
    """
    logger = logging.getLogger(__name__)
    start_time = time.time()
    
    logger.info(f"Processing query: '{query}'")
    logger.info(f"Output format: {output_format}")
    logger.info(f"Source: {source}")
    logger.info(f"Colors: {use_colors}")
    logger.info(f"Pagination: {use_pager} (page {page}, size {page_size})")
    if template:
        logger.info(f"Template: {template}")
    
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
        
        # Resolve template if provided
        resolved_template = None
        if template:
            predefined_templates = get_predefined_templates()
            if template in predefined_templates:
                resolved_template = predefined_templates[template]
                logger.info(f"Using predefined template '{template}': {resolved_template}")
            else:
                resolved_template = template
                logger.info(f"Using custom template: {resolved_template}")
        
        # Format and display results
        try:
            execution_time = time.time() - start_time
            output = format_query_results(
                matching_jobs, 
                criteria, 
                output_format=output_format,
                show_next_runs=True,
                use_colors=use_colors,
                page_size=page_size,
                page=page,
                use_pager=use_pager,
                template=resolved_template,
                verbose=verbose
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
    
    # Handle template info requests first
    template_result = handle_template_info_requests(args)
    if template_result is not None:
        return template_result
    
    # Validate that query is provided if not using template info commands
    if not args.query:
        parser.error("query argument is required (unless using --list-templates or --template-help)")
    
    # Set up logging
    setup_logging(args.verbose)
    logger = logging.getLogger(__name__)
    
    logger.debug("Starting cron-query application")
    logger.debug(f"Arguments: {args}")
    
    # Determine color usage
    use_colors = not args.no_color and is_color_supported()
    
    try:
        return process_query(
            query=args.query,
            output_format=args.format,
            source=args.source,
            file_path=args.file,
            use_colors=use_colors,
            page_size=args.page_size,
            page=args.page,
            use_pager=not args.no_pager,
            template=args.template,
            verbose=args.verbose
        )
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