#!/usr/bin/env python3
"""
Output Formatter - Formats query results for user-friendly display.

This module provides functionality to format cron job query results
in various output formats with human-readable descriptions.
"""

import logging
from datetime import datetime
from typing import List, Dict, Any, Optional
from enum import Enum

from .cron_loader import CronJob
from .query_parser import QueryCriteria, format_criteria_description
from .schedule_analyzer import get_next_runs, get_job_schedule_description


logger = logging.getLogger(__name__)


class OutputFormat(Enum):
    """Supported output formats."""
    LIST = "list"
    TABLE = "table"
    JSON = "json"


class OutputFormatterError(Exception):
    """Exception raised when output formatting fails."""
    pass


def format_query_results(
    jobs: List[CronJob], 
    criteria: QueryCriteria, 
    output_format: str = "list",
    show_next_runs: bool = True,
    max_next_runs: int = 3
) -> str:
    """
    Format query results for display.
    
    Args:
        jobs: List of matching cron jobs
        criteria: Query criteria that was used
        output_format: Output format ("list", "table", "json")
        show_next_runs: Whether to show next run times
        max_next_runs: Maximum number of next runs to show
        
    Returns:
        Formatted output string
        
    Raises:
        OutputFormatterError: If formatting fails
    """
    logger.debug(f"Formatting {len(jobs)} jobs in {output_format} format")
    
    try:
        if output_format == OutputFormat.LIST.value:
            return _format_list_output(jobs, criteria, show_next_runs, max_next_runs)
        elif output_format == OutputFormat.TABLE.value:
            return _format_table_output(jobs, criteria, show_next_runs, max_next_runs)
        elif output_format == OutputFormat.JSON.value:
            return _format_json_output(jobs, criteria, show_next_runs, max_next_runs)
        else:
            raise OutputFormatterError(f"Unsupported output format: {output_format}")
            
    except Exception as e:
        if isinstance(e, OutputFormatterError):
            raise
        raise OutputFormatterError(f"Failed to format output: {e}")


def _format_list_output(
    jobs: List[CronJob], 
    criteria: QueryCriteria, 
    show_next_runs: bool, 
    max_next_runs: int
) -> str:
    """Format output as a simple list."""
    if not jobs:
        return _format_empty_results(criteria)
    
    lines = []
    
    # Header
    query_desc = format_criteria_description(criteria)
    lines.append(f"Jobs matching '{query_desc}':")
    lines.append("")
    
    # Job entries
    for i, job in enumerate(jobs, 1):
        lines.append(f"{i}. {job.raw_line}")
        
        # Add human-readable description
        try:
            description = get_job_schedule_description(job)
            lines.append(f"   Schedule: {description}")
        except Exception as e:
            logger.warning(f"Failed to get schedule description for job: {e}")
            lines.append(f"   Schedule: {job.cron_expression}")
        
        # Add next run times
        if show_next_runs:
            try:
                next_runs = get_next_runs(job, max_next_runs)
                if next_runs:
                    next_run_strs = [run.strftime("%Y-%m-%d %H:%M") for run in next_runs[:max_next_runs]]
                    lines.append(f"   Next runs: {', '.join(next_run_strs)}")
            except Exception as e:
                logger.warning(f"Failed to get next runs for job: {e}")
        
        lines.append("")  # Blank line between jobs
    
    # Summary
    lines.append(f"Found {len(jobs)} matching job{'s' if len(jobs) != 1 else ''}.")
    
    return "\n".join(lines)


def _format_table_output(
    jobs: List[CronJob], 
    criteria: QueryCriteria, 
    show_next_runs: bool, 
    max_next_runs: int
) -> str:
    """Format output as a table."""
    if not jobs:
        return _format_empty_results(criteria)
    
    lines = []
    
    # Header
    query_desc = format_criteria_description(criteria)
    lines.append(f"Jobs matching '{query_desc}':")
    lines.append("")
    
    # Table header
    headers = ["#", "Expression", "Command", "Description"]
    if show_next_runs:
        headers.append("Next Run")
    
    # Calculate column widths
    col_widths = [len(h) for h in headers]
    
    # Prepare table data
    table_data = []
    for i, job in enumerate(jobs, 1):
        try:
            description = get_job_schedule_description(job)
        except:
            description = job.cron_expression
        
        next_run_str = ""
        if show_next_runs:
            try:
                next_runs = get_next_runs(job, 1)
                if next_runs:
                    next_run_str = next_runs[0].strftime("%Y-%m-%d %H:%M")
            except:
                next_run_str = "Error"
        
        row = [str(i), job.cron_expression, job.command, description]
        if show_next_runs:
            row.append(next_run_str)
        
        table_data.append(row)
        
        # Update column widths
        for j, cell in enumerate(row):
            col_widths[j] = max(col_widths[j], len(str(cell)))
    
    # Format table
    separator = "+" + "+".join("-" * (w + 2) for w in col_widths) + "+"
    
    lines.append(separator)
    
    # Header row
    header_row = "|" + "|".join(f" {h:<{col_widths[i]}} " for i, h in enumerate(headers)) + "|"
    lines.append(header_row)
    lines.append(separator)
    
    # Data rows
    for row in table_data:
        data_row = "|" + "|".join(f" {str(cell):<{col_widths[i]}} " for i, cell in enumerate(row)) + "|"
        lines.append(data_row)
    
    lines.append(separator)
    lines.append("")
    
    # Summary
    lines.append(f"Found {len(jobs)} matching job{'s' if len(jobs) != 1 else ''}.")
    
    return "\n".join(lines)


def _format_json_output(
    jobs: List[CronJob], 
    criteria: QueryCriteria, 
    show_next_runs: bool, 
    max_next_runs: int
) -> str:
    """Format output as JSON."""
    import json
    
    query_desc = format_criteria_description(criteria)
    
    result = {
        "query": {
            "description": query_desc,
            "raw_query": criteria.raw_query,
            "type": criteria.query_type.value
        },
        "matches": len(jobs),
        "jobs": []
    }
    
    for job in jobs:
        job_data = {
            "cron_expression": job.cron_expression,
            "command": job.command,
            "raw_line": job.raw_line,
            "user": job.user,
            "source": job.source
        }
        
        # Add schedule description
        try:
            job_data["schedule_description"] = get_job_schedule_description(job)
        except Exception as e:
            logger.warning(f"Failed to get schedule description: {e}")
            job_data["schedule_description"] = None
        
        # Add next run times
        if show_next_runs:
            try:
                next_runs = get_next_runs(job, max_next_runs)
                job_data["next_runs"] = [
                    {
                        "timestamp": run.isoformat(),
                        "human_readable": run.strftime("%Y-%m-%d %H:%M")
                    }
                    for run in next_runs[:max_next_runs]
                ]
            except Exception as e:
                logger.warning(f"Failed to get next runs: {e}")
                job_data["next_runs"] = []
        
        result["jobs"].append(job_data)
    
    return json.dumps(result, indent=2)


def _format_empty_results(criteria: QueryCriteria) -> str:
    """Format output for empty results."""
    query_desc = format_criteria_description(criteria)
    
    lines = [
        f"No jobs found matching '{query_desc}'.",
        "",
        "This could mean:",
        "• No cron jobs are scheduled for the specified criteria",
        "• The crontab is empty or inaccessible", 
        "• The query didn't match the expected format",
        "",
        "Try:",
        "• Check if you have any cron jobs: crontab -l",
        "• Use a broader query (e.g., 'which jobs run on weekdays')",
        "• Verify the query format matches supported patterns"
    ]
    
    return "\n".join(lines)


def format_error_message(error: Exception, query: str = "") -> str:
    """
    Format error messages for user-friendly display.
    
    Args:
        error: Exception that occurred
        query: Original query string (if available)
        
    Returns:
        Formatted error message
    """
    lines = [
        f"❌ Error: {str(error)}",
        ""
    ]
    
    if query:
        lines.extend([
            f"Query: '{query}'",
            ""
        ])
    
    lines.extend([
        "Troubleshooting:",
        "• Check that your query format is supported",
        "• Verify you have permission to read crontab",
        "• Try a simpler query to test basic functionality",
        "",
        "Examples of supported queries:",
        "  cron-query 'Saturday'",
        "  cron-query 'weekdays'", 
        "  cron-query '8 AM'",
        "  cron-query '20:30'"
    ])
    
    return "\n".join(lines)


def get_supported_formats() -> List[str]:
    """
    Get list of supported output formats.
    
    Returns:
        List of supported format names
    """
    return [fmt.value for fmt in OutputFormat]


def validate_output_format(format_name: str) -> bool:
    """
    Validate if output format is supported.
    
    Args:
        format_name: Name of output format to validate
        
    Returns:
        True if format is supported, False otherwise
    """
    try:
        OutputFormat(format_name)
        return True
    except ValueError:
        return False


def format_job_summary(jobs: List[CronJob]) -> str:
    """
    Format a brief summary of jobs.
    
    Args:
        jobs: List of cron jobs to summarize
        
    Returns:
        Brief summary string
    """
    if not jobs:
        return "No jobs found"
    
    sources = set(job.source for job in jobs)
    users = set(job.user for job in jobs if job.user)
    
    summary_parts = [f"{len(jobs)} job{'s' if len(jobs) != 1 else ''}"]
    
    if sources:
        source_list = sorted(sources)
        summary_parts.append(f"from {', '.join(source_list)}")
    
    if users:
        if len(users) == 1:
            summary_parts.append(f"(user: {list(users)[0]})")
        else:
            summary_parts.append(f"({len(users)} users)")
    
    return " ".join(summary_parts)


def format_execution_time(start_time: datetime, end_time: datetime) -> str:
    """
    Format execution time for performance reporting.
    
    Args:
        start_time: Query start time
        end_time: Query end time
        
    Returns:
        Formatted execution time string
    """
    duration = end_time - start_time
    total_ms = duration.total_seconds() * 1000
    
    if total_ms < 1000:
        return f"Completed in {total_ms:.1f}ms"
    else:
        return f"Completed in {duration.total_seconds():.2f}s"