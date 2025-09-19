#!/usr/bin/env python3
"""
Output Formatter - Formats query results for user-friendly display.

This module provides functionality to format cron job query results
in various output formats with human-readable descriptions, including
color-coded terminal output, custom templates, and multiple export formats.
"""

import csv
import io
import logging
import math
import os
import sys
yaml = None
try:
    import yaml
except ImportError:
    yaml = None

from datetime import datetime
from typing import List, Dict, Any, Optional, Tuple
from enum import Enum

try:
    from colorama import Fore, Back, Style, init
    init(autoreset=True)
    COLORAMA_AVAILABLE = True
except ImportError:
    # Fallback for when colorama is not available
    class _DummyColor:
        def __getattr__(self, name):
            return ""
    
    Fore = Back = Style = _DummyColor()
    COLORAMA_AVAILABLE = False

from .cron_loader import CronJob
from .query_parser import QueryCriteria, format_criteria_description
from .schedule_analyzer import get_next_runs, get_job_schedule_description


logger = logging.getLogger(__name__)


class OutputFormat(Enum):
    """Supported output formats."""
    LIST = "list"
    TABLE = "table"
    JSON = "json"
    CSV = "csv"
    YAML = "yaml"


class ColorConfig:
    """Color configuration for terminal output."""
    def __init__(self, enabled: bool = True):
        self.enabled = enabled and COLORAMA_AVAILABLE and self._is_tty()
    
    def _is_tty(self) -> bool:
        """Check if output is going to a terminal."""
        return hasattr(sys.stdout, 'isatty') and sys.stdout.isatty()
    
    @property
    def cron_expr(self) -> str:
        return Fore.CYAN if self.enabled else ""
    
    @property
    def command(self) -> str:
        return Fore.GREEN if self.enabled else ""
    
    @property
    def description(self) -> str:
        return Fore.YELLOW if self.enabled else ""
    
    @property
    def next_run(self) -> str:
        return Fore.BLUE if self.enabled else ""
    
    @property
    def header(self) -> str:
        return Style.BRIGHT if self.enabled else ""
    
    @property
    def error(self) -> str:
        return Fore.RED if self.enabled else ""
    
    @property
    def success(self) -> str:
        return Fore.GREEN if self.enabled else ""
    
    @property
    def warning(self) -> str:
        return Fore.YELLOW if self.enabled else ""
    
    @property
    def reset(self) -> str:
        return Style.RESET_ALL if self.enabled else ""


class PaginationConfig:
    """Configuration for output pagination."""
    def __init__(self, page_size: int = 20, enabled: bool = True):
        self.page_size = max(1, page_size)
        self.enabled = enabled
    
    def paginate_items(self, items: List[Any], page: int = 1) -> Tuple[List[Any], Dict[str, Any]]:
        """Paginate a list of items."""
        if not self.enabled:
            return items, {"total": len(items), "pages": 1, "current_page": 1}
        
        total_items = len(items)
        total_pages = math.ceil(total_items / self.page_size) if total_items > 0 else 1
        page = max(1, min(page, total_pages))
        
        start_idx = (page - 1) * self.page_size
        end_idx = start_idx + self.page_size
        
        return items[start_idx:end_idx], {
            "total": total_items,
            "pages": total_pages,
            "current_page": page,
            "has_prev": page > 1,
            "has_next": page < total_pages,
            "start_idx": start_idx + 1,
            "end_idx": min(end_idx, total_items)
        }


class OutputFormatterError(Exception):
    """Exception raised when output formatting fails."""
    pass


def format_query_results(
    jobs: List[CronJob], 
    criteria: QueryCriteria, 
    output_format: str = "list",
    show_next_runs: bool = True,
    max_next_runs: int = 3,
    use_colors: bool = True,
    page_size: int = 20,
    page: int = 1,
    use_pager: bool = True,
    template: Optional[str] = None,
    verbose: bool = False
) -> str:
    """
    Format query results for display.
    
    Args:
        jobs: List of matching cron jobs
        criteria: Query criteria that was used
        output_format: Output format ("list", "table", "json", "csv", "yaml")
        show_next_runs: Whether to show next run times
        max_next_runs: Maximum number of next runs to show
        use_colors: Whether to use colors in terminal output
        page_size: Number of items per page
        page: Current page number
        use_pager: Whether to paginate output
        template: Custom output template string
        verbose: Whether to show detailed cron parsing info
        
    Returns:
        Formatted output string
        
    Raises:
        OutputFormatterError: If formatting fails
    """
    logger.debug(f"Formatting {len(jobs)} jobs in {output_format} format")
    
    try:
        # Configure colors and pagination
        colors = ColorConfig(enabled=use_colors)
        pagination = PaginationConfig(page_size=page_size, enabled=use_pager)
        
        # If a custom template is provided, use template formatting
        if template and output_format in [OutputFormat.LIST.value, OutputFormat.TABLE.value]:
            return _format_template_output(jobs, criteria, template, show_next_runs, 
                                          max_next_runs, colors, pagination, page, verbose)

        # Use the appropriate formatter based on output format
        if output_format == OutputFormat.LIST.value:
            return _format_list_output(jobs, criteria, show_next_runs, max_next_runs, 
                                       colors, pagination, page, verbose)
        elif output_format == OutputFormat.TABLE.value:
            return _format_table_output(jobs, criteria, show_next_runs, max_next_runs, 
                                        colors, pagination, page, verbose)
        elif output_format == OutputFormat.JSON.value:
            # JSON output is not colored or paginated
            return _format_json_output(jobs, criteria, show_next_runs, max_next_runs, verbose)
        elif output_format == OutputFormat.CSV.value:
            return _format_csv_output(jobs, criteria, show_next_runs, max_next_runs, verbose)
        elif output_format == OutputFormat.YAML.value:
            if not yaml:
                raise OutputFormatterError("YAML output requires PyYAML package")
            return _format_yaml_output(jobs, criteria, show_next_runs, max_next_runs, verbose)
        else:
            raise OutputFormatterError(f"Unsupported output format: {output_format}")
            
    except Exception as e:
        if isinstance(e, OutputFormatterError):
            raise
        logger.exception("Error formatting output")
        raise OutputFormatterError(f"Failed to format output: {e}")


def _format_list_output(
    jobs: List[CronJob], 
    criteria: QueryCriteria, 
    show_next_runs: bool, 
    max_next_runs: int,
    colors: ColorConfig,
    pagination: PaginationConfig,
    page: int,
    verbose: bool
) -> str:
    """Format output as a simple list with color and pagination support."""
    if not jobs:
        return _format_empty_results(criteria, colors)
    
    lines = []
    
    # Header
    query_desc = format_criteria_description(criteria)
    lines.append(f"{colors.header}Jobs matching '{query_desc}':{colors.reset}")
    lines.append("")
    
    # Apply pagination if enabled
    paginated_jobs, page_info = pagination.paginate_items(jobs, page)
    
    # Job entries
    for i, job in enumerate(paginated_jobs, page_info.get('start_idx', 1)):
        # Job header with color
        lines.append(f"{colors.header}{i}. {colors.reset}{colors.cron_expr}{job.cron_expression}{colors.reset}  {colors.command}{job.command}{colors.reset}")
        
        # Add human-readable description
        try:
            description = get_job_schedule_description(job)
            lines.append(f"   {colors.description}Schedule: {description}{colors.reset}")
        except Exception as e:
            logger.warning(f"Failed to get schedule description for job: {e}")
            lines.append(f"   {colors.warning}Schedule: {job.cron_expression}{colors.reset}")
        
        # Add verbose parsing details if requested
        if verbose:
            lines.extend(_get_verbose_job_info(job, colors))
        
        # Add next run times
        if show_next_runs:
            try:
                next_runs = get_next_runs(job, max_next_runs)
                if next_runs:
                    next_run_strs = [run.strftime("%Y-%m-%d %H:%M") for run in next_runs[:max_next_runs]]
                    lines.append(f"   {colors.next_run}Next runs: {', '.join(next_run_strs)}{colors.reset}")
            except Exception as e:
                logger.warning(f"Failed to get next runs for job: {e}")
                lines.append(f"   {colors.error}Error getting next runs: {e}{colors.reset}")
        
        lines.append("")  # Blank line between jobs
    
    # Pagination info
    if pagination.enabled and page_info['pages'] > 1:
        lines.extend(_format_pagination_info(page_info, colors))
        lines.append("")
    
    # Summary
    if pagination.enabled:
        lines.append(f"{colors.success}Showing {page_info['start_idx']}-{page_info['end_idx']} of {page_info['total']} matching job{'s' if page_info['total'] != 1 else ''}.{colors.reset}")
    else:
        lines.append(f"{colors.success}Found {len(jobs)} matching job{'s' if len(jobs) != 1 else ''}.{colors.reset}")
    
    return "\n".join(lines)


def _format_table_output(
    jobs: List[CronJob], 
    criteria: QueryCriteria, 
    show_next_runs: bool, 
    max_next_runs: int,
    colors: ColorConfig,
    pagination: PaginationConfig,
    page: int,
    verbose: bool
) -> str:
    """Format output as a table with color and pagination support."""
    if not jobs:
        return _format_empty_results(criteria, colors)
    
    lines = []
    
    # Header
    query_desc = format_criteria_description(criteria)
    lines.append(f"{colors.header}Jobs matching '{query_desc}':{colors.reset}")
    lines.append("")
    
    # Apply pagination if enabled
    paginated_jobs, page_info = pagination.paginate_items(jobs, page)
    
    # Table header
    headers = ["#", "Expression", "Command", "Description"]
    if show_next_runs:
        headers.append("Next Run")
    if verbose:
        headers.extend(["User", "Source"])
    
    # Calculate column widths
    col_widths = [len(h) for h in headers]
    
    # Prepare table data
    table_data = []
    for i, job in enumerate(paginated_jobs, page_info.get('start_idx', 1)):
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
        
        # Truncate long fields for table display
        command = job.command[:40] + "..." if len(job.command) > 40 else job.command
        description = description[:50] + "..." if len(description) > 50 else description
        
        row = [str(i), job.cron_expression, command, description]
        if show_next_runs:
            row.append(next_run_str)
        if verbose:
            row.extend([job.user or "N/A", job.source])
        
        table_data.append(row)
        
        # Update column widths
        for j, cell in enumerate(row):
            col_widths[j] = max(col_widths[j], len(str(cell)))
    
    # Format table with colors
    separator = "+" + "+".join("-" * (w + 2) for w in col_widths) + "+"
    
    lines.append(separator)
    
    # Header row with color
    header_cells = []
    for i, h in enumerate(headers):
        header_cells.append(f" {colors.header}{h:<{col_widths[i]}}{colors.reset} ")
    header_row = "|" + "|".join(header_cells) + "|"
    lines.append(header_row)
    lines.append(separator)
    
    # Data rows with colors
    for row in table_data:
        colored_cells = []
        for i, cell in enumerate(row):
            cell_str = str(cell)
            # Apply colors based on column type
            if i == 0:  # Index
                colored_cell = f" {colors.header}{cell_str:<{col_widths[i]}}{colors.reset} "
            elif i == 1:  # Cron expression
                colored_cell = f" {colors.cron_expr}{cell_str:<{col_widths[i]}}{colors.reset} "
            elif i == 2:  # Command
                colored_cell = f" {colors.command}{cell_str:<{col_widths[i]}}{colors.reset} "
            elif headers[i] == "Description":
                colored_cell = f" {colors.description}{cell_str:<{col_widths[i]}}{colors.reset} "
            elif headers[i] == "Next Run":
                colored_cell = f" {colors.next_run}{cell_str:<{col_widths[i]}}{colors.reset} "
            else:
                colored_cell = f" {cell_str:<{col_widths[i]}} "
            colored_cells.append(colored_cell)
        
        data_row = "|" + "|".join(colored_cells) + "|"
        lines.append(data_row)
    
    lines.append(separator)
    
    # Pagination info
    if pagination.enabled and page_info['pages'] > 1:
        lines.append("")
        lines.extend(_format_pagination_info(page_info, colors))
    
    lines.append("")
    
    # Summary
    if pagination.enabled:
        lines.append(f"{colors.success}Showing {page_info['start_idx']}-{page_info['end_idx']} of {page_info['total']} matching job{'s' if page_info['total'] != 1 else ''}.{colors.reset}")
    else:
        lines.append(f"{colors.success}Found {len(jobs)} matching job{'s' if len(jobs) != 1 else ''}.{colors.reset}")
    
    return "\n".join(lines)


def _format_json_output(
    jobs: List[CronJob], 
    criteria: QueryCriteria, 
    show_next_runs: bool, 
    max_next_runs: int,
    verbose: bool
) -> str:
    """Format output as JSON with optional verbose details."""
    import json
    
    query_desc = format_criteria_description(criteria)
    
    result = {
        "query": {
            "description": query_desc,
            "raw_query": criteria.raw_query,
            "type": criteria.query_type.value
        },
        "matches": len(jobs),
        "timestamp": datetime.now().isoformat(),
        "jobs": []
    }
    
    # Add verbose query details if requested
    if verbose:
        result["query"]["parsed_criteria"] = {
            "days_of_week": list(criteria.days_of_week) if criteria.days_of_week else None,
            "time_hour": criteria.time_hour,
            "time_minute": criteria.time_minute,
            "specific_date": criteria.specific_date.isoformat() if criteria.specific_date else None,
            "time_range_start": criteria.time_range_start,
            "time_range_end": criteria.time_range_end,
            "is_specific_date": criteria.is_specific_date,
            "weekdays_only": criteria.weekdays_only,
            "weekends_only": criteria.weekends_only,
            "is_time_after": criteria.is_time_after,
            "is_time_before": criteria.is_time_before,
            "is_time_between": criteria.is_time_between
        }
    
    for job in jobs:
        job_data = {
            "cron_expression": job.cron_expression,
            "command": job.command,
            "raw_line": job.raw_line,
            "user": job.user,
            "source": job.source
        }
        
        # Add verbose job parsing details if requested
        if verbose:
            try:
                from croniter import croniter
                fields = job.cron_expression.split()
                if len(fields) >= 5:
                    job_data["parsed_fields"] = {
                        "minute": fields[0],
                        "hour": fields[1],
                        "day_of_month": fields[2],
                        "month": fields[3],
                        "day_of_week": fields[4]
                    }
                    if len(fields) > 5:
                        job_data["parsed_fields"]["year"] = " ".join(fields[5:])
            except Exception as e:
                logger.warning(f"Failed to parse cron fields: {e}")
        
        # Add schedule description
        try:
            job_data["schedule_description"] = get_job_schedule_description(job)
        except Exception as e:
            logger.warning(f"Failed to get schedule description: {e}")
            job_data["schedule_description"] = None
            if verbose:
                job_data["parsing_error"] = str(e)
        
        # Add next run times
        if show_next_runs:
            try:
                next_runs = get_next_runs(job, max_next_runs)
                job_data["next_runs"] = [
                    {
                        "timestamp": run.isoformat(),
                        "human_readable": run.strftime("%Y-%m-%d %H:%M"),
                        "day_of_week": run.strftime("%A")
                    }
                    for run in next_runs[:max_next_runs]
                ]
            except Exception as e:
                logger.warning(f"Failed to get next runs: {e}")
                job_data["next_runs"] = []
                if verbose:
                    job_data["next_runs_error"] = str(e)
        
        result["jobs"].append(job_data)
    
    return json.dumps(result, indent=2)


def _format_csv_output(
    jobs: List[CronJob], 
    criteria: QueryCriteria, 
    show_next_runs: bool, 
    max_next_runs: int,
    verbose: bool
) -> str:
    """Format output as CSV."""
    output = io.StringIO()
    
    # Prepare headers
    headers = ["cron_expression", "command", "schedule_description"]
    if show_next_runs:
        for i in range(max_next_runs):
            headers.append(f"next_run_{i+1}")
    if verbose:
        headers.extend(["user", "source", "raw_line"])
    
    writer = csv.writer(output)
    writer.writerow(headers)
    
    # Write job data
    for job in jobs:
        row = [job.cron_expression, job.command]
        
        # Add schedule description
        try:
            description = get_job_schedule_description(job)
            row.append(description)
        except:
            row.append(job.cron_expression)
        
        # Add next run times
        if show_next_runs:
            try:
                next_runs = get_next_runs(job, max_next_runs)
                for i in range(max_next_runs):
                    if i < len(next_runs):
                        row.append(next_runs[i].strftime("%Y-%m-%d %H:%M"))
                    else:
                        row.append("")
            except:
                for i in range(max_next_runs):
                    row.append("")
        
        # Add verbose info
        if verbose:
            row.extend([job.user or "", job.source, job.raw_line])
        
        writer.writerow(row)
    
    return output.getvalue()


def _format_yaml_output(
    jobs: List[CronJob], 
    criteria: QueryCriteria, 
    show_next_runs: bool, 
    max_next_runs: int,
    verbose: bool
) -> str:
    """Format output as YAML."""
    query_desc = format_criteria_description(criteria)
    
    result = {
        "query": {
            "description": query_desc,
            "raw_query": criteria.raw_query,
            "type": criteria.query_type.value,
            "timestamp": datetime.now().isoformat()
        },
        "summary": {
            "matches": len(jobs)
        },
        "jobs": []
    }
    
    # Add verbose query details if requested
    if verbose:
        result["query"]["parsed_criteria"] = {
            "days_of_week": list(criteria.days_of_week) if criteria.days_of_week else None,
            "time_hour": criteria.time_hour,
            "time_minute": criteria.time_minute,
            "specific_date": criteria.specific_date.isoformat() if criteria.specific_date else None,
            "time_range_start": criteria.time_range_start,
            "time_range_end": criteria.time_range_end,
            "is_specific_date": criteria.is_specific_date,
            "weekdays_only": criteria.weekdays_only,
            "weekends_only": criteria.weekends_only,
            "is_time_after": criteria.is_time_after,
            "is_time_before": criteria.is_time_before,
            "is_time_between": criteria.is_time_between
        }
    
    for job in jobs:
        job_data = {
            "cron_expression": job.cron_expression,
            "command": job.command
        }
        
        # Add schedule description
        try:
            job_data["schedule_description"] = get_job_schedule_description(job)
        except Exception as e:
            job_data["schedule_description"] = None
            if verbose:
                job_data["parsing_error"] = str(e)
        
        # Add next run times
        if show_next_runs:
            try:
                next_runs = get_next_runs(job, max_next_runs)
                job_data["next_runs"] = [
                    {
                        "datetime": run.isoformat(),
                        "human_readable": run.strftime("%Y-%m-%d %H:%M"),
                        "day_of_week": run.strftime("%A")
                    }
                    for run in next_runs[:max_next_runs]
                ]
            except Exception as e:
                job_data["next_runs"] = []
                if verbose:
                    job_data["next_runs_error"] = str(e)
        
        # Add verbose info
        if verbose:
            job_data["user"] = job.user
            job_data["source"] = job.source
            job_data["raw_line"] = job.raw_line
            
            # Parse cron fields
            try:
                fields = job.cron_expression.split()
                if len(fields) >= 5:
                    job_data["parsed_fields"] = {
                        "minute": fields[0],
                        "hour": fields[1],
                        "day_of_month": fields[2],
                        "month": fields[3],
                        "day_of_week": fields[4]
                    }
            except:
                pass
        
        result["jobs"].append(job_data)
    
    return yaml.dump(result, default_flow_style=False, indent=2, sort_keys=False)


def _get_bullet_point() -> str:
    """Get the appropriate bullet point character based on terminal type."""
    term = os.getenv('TERM', '').lower()
    if term in ['xterm', 'xterm-color', 'xterm-16color', 'xterm-256color']:
        return '*'  # Use ASCII bullet for xterm
    return '•'  # Use Unicode bullet for other terminals

def _format_empty_results(criteria: QueryCriteria, colors: Optional[ColorConfig] = None) -> str:
    """Format output for empty results."""
    if colors is None:
        colors = ColorConfig(enabled=False)
        
    query_desc = format_criteria_description(criteria)
    bullet = _get_bullet_point()
    
    lines = [
        f"{colors.warning}No jobs found matching '{query_desc}'.{colors.reset}",
        "",
        "This could mean:",
        f"{bullet} No cron jobs are scheduled for the specified criteria",
        f"{bullet} The crontab is empty or inaccessible", 
        f"{bullet} The query didn't match the expected format",
        "",
        "Try:",
        f"{bullet} Check if you have any cron jobs: crontab -l",
        f"{bullet} Use a broader query (e.g., 'which jobs run on weekdays')",
        f"{bullet} Verify the query format matches supported patterns"
    ]
    
    return "\n".join(lines)


def _get_verbose_job_info(job: CronJob, colors: ColorConfig) -> List[str]:
    """Get verbose parsing information for a job."""
    lines = []
    
    # Parse cron fields
    try:
        fields = job.cron_expression.split()
        if len(fields) >= 5:
            lines.append(f"   {colors.description}Cron fields:{colors.reset}")
            lines.append(f"     Minute: {colors.cron_expr}{fields[0]}{colors.reset}")
            lines.append(f"     Hour: {colors.cron_expr}{fields[1]}{colors.reset}")
            lines.append(f"     Day of Month: {colors.cron_expr}{fields[2]}{colors.reset}")
            lines.append(f"     Month: {colors.cron_expr}{fields[3]}{colors.reset}")
            lines.append(f"     Day of Week: {colors.cron_expr}{fields[4]}{colors.reset}")
    except Exception as e:
        lines.append(f"   {colors.error}Failed to parse cron fields: {e}{colors.reset}")
    
    # Add source information
    if job.user:
        lines.append(f"   {colors.description}User: {colors.reset}{job.user}")
    lines.append(f"   {colors.description}Source: {colors.reset}{job.source}")
    
    return lines


def _format_pagination_info(page_info: Dict[str, Any], colors: ColorConfig) -> List[str]:
    """Format pagination information."""
    lines = []
    
    nav_parts = []
    if page_info['has_prev']:
        nav_parts.append(f"{colors.cron_expr}←{colors.reset} Previous")
    
    nav_parts.append(f"Page {colors.header}{page_info['current_page']}{colors.reset} of {colors.header}{page_info['pages']}{colors.reset}")
    
    if page_info['has_next']:
        nav_parts.append(f"Next {colors.cron_expr}→{colors.reset}")
    
    lines.append(" | ".join(nav_parts))
    
    return lines


def _format_template_output(
    jobs: List[CronJob],
    criteria: QueryCriteria,
    template: str,
    show_next_runs: bool,
    max_next_runs: int,
    colors: ColorConfig,
    pagination: PaginationConfig,
    page: int,
    verbose: bool
) -> str:
    """Format output using a custom template."""
    if not jobs:
        return _format_empty_results(criteria, colors)
    
    lines = []
    
    # Header
    query_desc = format_criteria_description(criteria)
    lines.append(f"{colors.header}Jobs matching '{query_desc}':{colors.reset}")
    lines.append("")
    
    # Apply pagination if enabled
    paginated_jobs, page_info = pagination.paginate_items(jobs, page)
    
    # Process each job with the template
    for i, job in enumerate(paginated_jobs, page_info.get('start_idx', 1)):
        # Prepare template variables
        template_vars = {
            'index': str(i),
            'expression': job.cron_expression,
            'command': job.command,
            'raw_line': job.raw_line,
            'user': job.user or 'N/A',
            'source': job.source
        }
        
        # Add schedule description
        try:
            template_vars['description'] = get_job_schedule_description(job)
        except:
            template_vars['description'] = job.cron_expression
        
        # Add next runs
        if show_next_runs:
            try:
                next_runs = get_next_runs(job, max_next_runs)
                template_vars['next_runs'] = ', '.join(
                    run.strftime('%Y-%m-%d %H:%M') for run in next_runs[:max_next_runs]
                )
                if next_runs:
                    template_vars['next_run'] = next_runs[0].strftime('%Y-%m-%d %H:%M')
                else:
                    template_vars['next_run'] = 'N/A'
            except:
                template_vars['next_runs'] = 'Error'
                template_vars['next_run'] = 'Error'
        else:
            template_vars['next_runs'] = 'N/A'
            template_vars['next_run'] = 'N/A'
        
        # Apply colors to template variables
        colored_vars = {
            'index': f"{colors.header}{template_vars['index']}{colors.reset}",
            'expression': f"{colors.cron_expr}{template_vars['expression']}{colors.reset}",
            'command': f"{colors.command}{template_vars['command']}{colors.reset}",
            'description': f"{colors.description}{template_vars['description']}{colors.reset}",
            'next_runs': f"{colors.next_run}{template_vars['next_runs']}{colors.reset}",
            'next_run': f"{colors.next_run}{template_vars['next_run']}{colors.reset}",
            'user': template_vars['user'],
            'source': template_vars['source'],
            'raw_line': template_vars['raw_line']
        }
        
        # Format the template
        try:
            formatted_line = template.format(**colored_vars)
            lines.append(formatted_line)
        except KeyError as e:
            lines.append(f"{colors.error}Template error: Unknown variable {e}{colors.reset}")
        except Exception as e:
            lines.append(f"{colors.error}Template formatting error: {e}{colors.reset}")
        
        lines.append("")  # Blank line between jobs
    
    # Pagination info
    if pagination.enabled and page_info['pages'] > 1:
        lines.extend(_format_pagination_info(page_info, colors))
        lines.append("")
    
    # Summary
    if pagination.enabled:
        lines.append(f"{colors.success}Showing {page_info['start_idx']}-{page_info['end_idx']} of {page_info['total']} matching job{'s' if page_info['total'] != 1 else ''}.{colors.reset}")
    else:
        lines.append(f"{colors.success}Found {len(jobs)} matching job{'s' if len(jobs) != 1 else ''}.{colors.reset}")
    
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
    bullet = _get_bullet_point()
    error_symbol = 'X' if os.getenv('TERM', '').lower().startswith('xterm') else '❌'
    
    lines = [
        f"{error_symbol} Error: {str(error)}",
        ""
    ]
    
    if query:
        lines.extend([
            f"Query: '{query}'",
            ""
        ])
    
    lines.extend([
        "Troubleshooting:",
        f"{bullet} Check that your query format is supported",
        f"{bullet} Verify you have permission to read crontab",
        f"{bullet} Try a simpler query to test basic functionality",
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
    formats = [fmt.value for fmt in OutputFormat]
    # Remove YAML if PyYAML is not available
    if not yaml and 'yaml' in formats:
        formats.remove('yaml')
    return formats


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


def get_predefined_templates() -> Dict[str, str]:
    """
    Get dictionary of predefined output templates.
    
    Returns:
        Dictionary mapping template names to template strings
    """
    return {
        'compact': '{index}. {expression} -> {command}',
        'detailed': '{index}. {expression}\n   Command: {command}\n   Schedule: {description}\n   Next run: {next_run}',
        'summary': '{expression} | {description} | Next: {next_run}',
        'csv_like': '{expression},{command},{description},{next_run}',
        'verbose': '{index}. {expression}\n   Command: {command}\n   Description: {description}\n   User: {user} | Source: {source}\n   Next runs: {next_runs}'
    }


def get_template_help() -> str:
    """
    Get help text for template usage.
    
    Returns:
        Help text explaining template variables and examples
    """
    return """Available template variables:
  {index}       - Job number
  {expression}  - Cron expression
  {command}     - Command to execute
  {description} - Human-readable schedule description
  {next_run}    - Next scheduled run time
  {next_runs}   - All next run times (comma-separated)
  {user}        - Job owner (if available)
  {source}      - Job source (user/system/file)
  {raw_line}    - Original cron line

Predefined templates:
  compact   - {index}. {expression} -> {command}
  detailed  - Multi-line with full details
  summary   - Pipe-separated summary
  verbose   - All available information

Example custom template:
  "Job {index}: {expression} runs {description}"
  """


def is_color_supported() -> bool:
    """
    Check if color output is supported in the current environment.
    
    Returns:
        True if colors are supported, False otherwise
    """
    return COLORAMA_AVAILABLE and (
        os.getenv('TERM', '').lower() != 'dumb' and
        not os.getenv('NO_COLOR') and
        hasattr(sys.stdout, 'isatty') and
        sys.stdout.isatty()
    )
