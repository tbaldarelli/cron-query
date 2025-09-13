#!/usr/bin/env python3
"""
Cron Data Loader - Handles loading and parsing crontab data.

This module provides functionality to load crontab data from various sources
and parse individual cron lines into structured data objects.
"""

import logging
import subprocess
import sys
from dataclasses import dataclass
from typing import List, Optional, Dict, Any
from croniter import croniter


logger = logging.getLogger(__name__)


@dataclass
class CronJob:
    """
    Represents a single cron job with its schedule and command.
    
    Attributes:
        minute: Minute field (0-59 or *)
        hour: Hour field (0-23 or *)
        day_of_month: Day of month field (1-31 or *)
        month: Month field (1-12 or *)
        day_of_week: Day of week field (0-7 or *, where 0 and 7 are Sunday)
        command: The command to execute
        raw_line: Original cron line for reference
        user: User who owns this cron job (optional)
        source: Source of the cron job (user, system, etc.)
    """
    minute: str
    hour: str
    day_of_month: str
    month: str
    day_of_week: str
    command: str
    raw_line: str
    user: Optional[str] = None
    source: str = "user"
    
    @property
    def cron_expression(self) -> str:
        """Return the standard 5-field cron expression."""
        return f"{self.minute} {self.hour} {self.day_of_month} {self.month} {self.day_of_week}"
    
    @property
    def is_valid(self) -> bool:
        """Check if this cron job has a valid expression."""
        try:
            croniter(self.cron_expression)
            return True
        except Exception:
            return False


# Special cron keywords mapping to standard expressions
SPECIAL_KEYWORDS = {
    "@yearly": "0 0 1 1 *",
    "@annually": "0 0 1 1 *",
    "@monthly": "0 0 1 * *",
    "@weekly": "0 0 * * 0",
    "@daily": "0 0 * * *",
    "@midnight": "0 0 * * *",
    "@hourly": "0 * * * *",
}


class CronParseError(Exception):
    """Exception raised when parsing a cron line fails."""
    pass


def parse_cron_line(line: str, source: str = "user", user: Optional[str] = None) -> Optional[CronJob]:
    """
    Parse a single cron line into a CronJob object.
    
    Args:
        line: Raw cron line from crontab
        source: Source of the cron job (user, system, etc.)
        user: User who owns this cron job
        
    Returns:
        CronJob object or None if line should be skipped
        
    Raises:
        CronParseError: If the line cannot be parsed
    """
    original_line = line.strip()
    
    # Skip empty lines and comments
    if not original_line or original_line.startswith('#'):
        return None
    
    # Skip environment variable assignments
    if '=' in original_line and not ' ' in original_line.split('=')[0]:
        logger.debug(f"Skipping environment variable: {original_line}")
        return None
    
    try:
        # Handle special keywords
        if original_line.startswith('@'):
            parts = original_line.split(None, 1)
            if len(parts) < 2:
                raise CronParseError(f"Invalid special keyword format: {original_line}")
            
            keyword = parts[0].lower()
            command = parts[1]
            
            if keyword not in SPECIAL_KEYWORDS:
                raise CronParseError(f"Unknown special keyword: {keyword}")
            
            # Convert to standard format
            cron_expr = SPECIAL_KEYWORDS[keyword]
            minute, hour, day_of_month, month, day_of_week = cron_expr.split()
            
            return CronJob(
                minute=minute,
                hour=hour,
                day_of_month=day_of_month,
                month=month,
                day_of_week=day_of_week,
                command=command,
                raw_line=original_line,
                user=user,
                source=source
            )
        
        # Handle standard cron format: minute hour day_of_month month day_of_week command
        parts = original_line.split(None, 5)
        if len(parts) < 6:
            raise CronParseError(f"Invalid cron format - expected 6 fields, got {len(parts)}: {original_line}")
        
        minute, hour, day_of_month, month, day_of_week, command = parts
        
        # Basic field validation
        _validate_cron_fields(minute, hour, day_of_month, month, day_of_week)
        
        cron_job = CronJob(
            minute=minute,
            hour=hour,
            day_of_month=day_of_month,
            month=month,
            day_of_week=day_of_week,
            command=command,
            raw_line=original_line,
            user=user,
            source=source
        )
        
        # Validate the complete expression
        if not cron_job.is_valid:
            raise CronParseError(f"Invalid cron expression: {cron_job.cron_expression}")
        
        return cron_job
        
    except Exception as e:
        if isinstance(e, CronParseError):
            raise
        raise CronParseError(f"Failed to parse cron line '{original_line}': {e}")


def _validate_cron_fields(minute: str, hour: str, day_of_month: str, month: str, day_of_week: str) -> None:
    """
    Basic validation of cron fields.
    
    Args:
        minute: Minute field
        hour: Hour field  
        day_of_month: Day of month field
        month: Month field
        day_of_week: Day of week field
        
    Raises:
        CronParseError: If any field is obviously invalid
    """
    # This is basic validation - croniter will do the heavy lifting
    fields = {
        'minute': minute,
        'hour': hour,
        'day_of_month': day_of_month,
        'month': month,
        'day_of_week': day_of_week
    }
    
    for field_name, field_value in fields.items():
        if not field_value or field_value.isspace():
            raise CronParseError(f"Empty {field_name} field")


def load_user_crontab(user: Optional[str] = None) -> List[CronJob]:
    """
    Load crontab entries for the specified user (or current user).
    
    On Windows, this function provides mock data for development purposes.
    On Linux, it executes 'crontab -l' to get real data.
    
    Args:
        user: Username to load crontab for (None for current user)
        
    Returns:
        List of CronJob objects
        
    Raises:
        subprocess.CalledProcessError: If crontab command fails
    """
    logger.info(f"Loading crontab for user: {user or 'current user'}")
    
    # Mock data for Windows development
    if sys.platform == 'win32':
        logger.info("Running on Windows - using mock crontab data")
        return _get_mock_crontab_data(user)
    
    # Real implementation for Linux
    try:
        cmd = ['crontab', '-l']
        if user:
            cmd.extend(['-u', user])
        
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            check=True
        )
        
        cron_jobs = []
        for line_num, line in enumerate(result.stdout.splitlines(), 1):
            try:
                cron_job = parse_cron_line(line, source="user", user=user)
                if cron_job:
                    cron_jobs.append(cron_job)
            except CronParseError as e:
                logger.warning(f"Failed to parse crontab line {line_num}: {e}")
        
        logger.info(f"Loaded {len(cron_jobs)} cron jobs from user crontab")
        return cron_jobs
        
    except subprocess.CalledProcessError as e:
        if e.returncode == 1 and "no crontab for" in e.stderr.lower():
            logger.info(f"No crontab found for user {user or 'current user'}")
            return []
        raise


def _get_mock_crontab_data(user: Optional[str] = None) -> List[CronJob]:
    """
    Provide mock crontab data for Windows development.
    
    Args:
        user: Username (for mock purposes)
        
    Returns:
        List of mock CronJob objects
    """
    mock_lines = [
        "# Example crontab entries for testing",
        "0 2 * * 6 /home/user/backup.sh",  # Every Saturday at 2 AM
        "@daily /home/user/daily-cleanup.sh",  # Daily at midnight
        "30 8 * * 1-5 /home/user/weekday-report.sh",  # Weekdays at 8:30 AM
        "0 0 1 * * /home/user/monthly-report.sh",  # First of month at midnight
        "*/15 * * * * /home/user/check-status.sh",  # Every 15 minutes
        "@hourly /home/user/hourly-task.sh",  # Every hour
        "0 18 * * 5 /home/user/friday-evening.sh",  # Fridays at 6 PM
        "",  # Empty line (should be skipped)
        "# Another comment",
        "MAILTO=user@example.com",  # Environment variable (should be skipped)
        "45 23 * * * /home/user/late-night.sh",  # Every day at 11:45 PM
    ]
    
    cron_jobs = []
    for line in mock_lines:
        try:
            cron_job = parse_cron_line(line, source="user", user=user)
            if cron_job:
                cron_jobs.append(cron_job)
        except CronParseError as e:
            logger.warning(f"Mock data parse error: {e}")
    
    return cron_jobs