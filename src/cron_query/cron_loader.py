#!/usr/bin/env python3
"""
Cron Data Loader - Handles loading and parsing crontab data.

This module provides functionality to load crontab data from various sources
and parse individual cron lines into structured data objects.
"""

import logging
import os
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
        # Or system cron format: minute hour day_of_month month day_of_week user command
        parts = original_line.split()
        
        if len(parts) < 6:
            raise CronParseError(f"Invalid cron format - expected 6 or 7 fields, got {len(parts)}: {original_line}")
        elif len(parts) == 6:
            # Standard user crontab format (6 fields)
            minute, hour, day_of_month, month, day_of_week, command = parts
            parsed_user = user
        else:
            # Could be standard format with arguments or system format
            minute, hour, day_of_month, month, day_of_week = parts[:5]

            # If explicitly reading system sources, prefer system format parsing when field 6 looks like a username
            potential_user = parts[5]
            looks_like_path = (potential_user.startswith('/') or potential_user.startswith('./') or potential_user.startswith('~') or '=' in potential_user)
            if source == 'system' and len(parts) >= 7 and not looks_like_path:
                parsed_user = potential_user
                command = ' '.join(parts[6:])
            else:
                # Heuristic: if 6th field looks like a command path or starts with special chars,
                # treat it as standard format with command arguments
                if looks_like_path:
                    parsed_user = user
                    command = ' '.join(parts[5:])
                elif potential_user in ['root', 'www-data', 'nobody', 'daemon', 'mail', 'news', 'uucp', 'proxy', 'backup', 'list', 'man'] and len(parts) >= 7:
                    # Recognized system user
                    parsed_user = potential_user
                    command = ' '.join(parts[6:])
                else:
                    # Default to standard format with arguments
                    parsed_user = user
                    command = ' '.join(parts[5:])
        
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
            user=parsed_user,
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


def load_crontab_from_file(file_path: str) -> List[CronJob]:
    """
    Load crontab entries from a file.
    
    Args:
        file_path: Path to the crontab file to load
        
    Returns:
        List of CronJob objects parsed from the file
        
    Raises:
        FileNotFoundError: If the file doesn't exist
        PermissionError: If the file can't be read
        CronParseError: If any cron lines are invalid
    """
    import os
    
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Crontab file not found: {file_path}")
    
    if not os.path.isfile(file_path):
        raise ValueError(f"Path is not a file: {file_path}")
    
    logger.info(f"Loading crontab from file: {file_path}")
    
    jobs = []
    parse_errors = []
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            for line_number, line in enumerate(f, 1):
                try:
                    job = parse_cron_line(line.strip(), source="system", user=None)
                    if job:  # Skip None results (comments, empty lines, etc.)
                        jobs.append(job)
                        logger.debug(f"Parsed job from line {line_number}: {job.raw_line}")
                except CronParseError as e:
                    error_msg = f"Line {line_number}: {e}"
                    logger.warning(error_msg)
                    parse_errors.append(error_msg)
    
    except UnicodeDecodeError as e:
        raise CronParseError(f"File encoding error: {e}")
    except PermissionError:
        raise PermissionError(f"Permission denied reading file: {file_path}")
    except Exception as e:
        raise CronParseError(f"Error reading crontab file: {e}")
    
    if parse_errors:
        logger.warning(f"Encountered {len(parse_errors)} parsing errors in {file_path}")
        for error in parse_errors[:5]:  # Log first 5 errors
            logger.warning(f"  {error}")
        if len(parse_errors) > 5:
            logger.warning(f"  ... and {len(parse_errors) - 5} more errors")
    
    logger.info(f"Successfully loaded {len(jobs)} cron jobs from {file_path}")
    
    if parse_errors and not jobs:
        raise CronParseError(f"No valid cron jobs found in {file_path}. All lines had parsing errors.")
    
    return jobs


def load_system_crontabs() -> List[CronJob]:
    """
    Load system-wide crontab entries from /etc/crontab and /etc/cron.d/*.
    
    On Windows, this function provides mock data for development purposes.
    On Linux, it reads actual system crontab files.
    
    Returns:
        List of CronJob objects from system crontabs
        
    Raises:
        PermissionError: If system files cannot be read due to permissions
        CronParseError: If critical parsing errors occur
    """
    logger.info("Loading system crontab entries")
    
    # Mock data for Windows development
    if sys.platform == 'win32':
        logger.info("Running on Windows - using mock system crontab data")
        return _get_mock_system_crontab_data()
    
    cron_jobs = []
    errors = []
    
    # Load /etc/crontab
    try:
        etc_crontab_jobs = _load_etc_crontab()
        cron_jobs.extend(etc_crontab_jobs)
        logger.info(f"Loaded {len(etc_crontab_jobs)} jobs from /etc/crontab")
    except Exception as e:
        error_msg = f"Failed to load /etc/crontab: {e}"
        logger.warning(error_msg)
        errors.append(error_msg)
    
    # Load /etc/cron.d/* files
    try:
        cron_d_jobs = _load_cron_d_directory()
        cron_jobs.extend(cron_d_jobs)
        logger.info(f"Loaded {len(cron_d_jobs)} jobs from /etc/cron.d/")
    except Exception as e:
        error_msg = f"Failed to load /etc/cron.d/ directory: {e}"
        logger.warning(error_msg)
        errors.append(error_msg)
    
    if not cron_jobs and errors:
        raise PermissionError(f"Could not load any system crontabs. Errors: {'; '.join(errors)}")
    
    logger.info(f"Total system cron jobs loaded: {len(cron_jobs)}")
    return cron_jobs


def _load_etc_crontab() -> List[CronJob]:
    """
    Load cron jobs from /etc/crontab.
    
    Returns:
        List of CronJob objects
        
    Raises:
        FileNotFoundError: If /etc/crontab doesn't exist
        PermissionError: If /etc/crontab can't be read
    """
    etc_crontab_path = os.path.join(os.path.sep, "etc", "crontab")
    
    if not os.path.exists(etc_crontab_path):
        logger.info(f"No {etc_crontab_path} found")
        return []
    
    logger.debug(f"Reading {etc_crontab_path}")
    
    jobs = []
    parse_errors = []
    
    try:
        with open(etc_crontab_path, 'r', encoding='utf-8') as f:
            for line_number, line in enumerate(f, 1):
                try:
                    job = parse_cron_line(line.strip(), source="system", user=None)
                    if job:
                        jobs.append(job)
                        logger.debug(f"Parsed system job from {etc_crontab_path}:{line_number}: {job.raw_line}")
                except CronParseError as e:
                    error_msg = f"{etc_crontab_path}:{line_number}: {e}"
                    logger.debug(error_msg)
                    parse_errors.append(error_msg)
    
    except PermissionError:
        raise PermissionError(f"Permission denied reading {etc_crontab_path}")
    except Exception as e:
        raise CronParseError(f"Error reading {etc_crontab_path}: {e}")
    
    if parse_errors:
        logger.debug(f"Encountered {len(parse_errors)} parsing errors in {etc_crontab_path}")
    
    return jobs


def _load_cron_d_directory() -> List[CronJob]:
    """
    Load cron jobs from all files in /etc/cron.d/ directory.
    
    Returns:
        List of CronJob objects
        
    Raises:
        PermissionError: If directory can't be accessed
    """
    import os
    import glob
    
    cron_d_path = os.path.join(os.path.sep, "etc", "cron.d")
    
    if not os.path.exists(cron_d_path):
        logger.info(f"No {cron_d_path} directory found")
        return []
    
    if not os.path.isdir(cron_d_path):
        logger.warning(f"{cron_d_path} exists but is not a directory")
        return []
    
    logger.debug(f"Scanning {cron_d_path} directory")
    
    jobs = []
    
    try:
        # Get all files in /etc/cron.d/ (excluding hidden files)
        pattern = os.path.join(cron_d_path, "*")
        cron_files = [f for f in glob.glob(pattern) if os.path.isfile(f) and not os.path.basename(f).startswith('.')]
        
        logger.debug(f"Found {len(cron_files)} files in {cron_d_path}")
        
        for cron_file in sorted(cron_files):
            try:
                file_jobs = _load_cron_d_file(cron_file)
                jobs.extend(file_jobs)
                logger.debug(f"Loaded {len(file_jobs)} jobs from {cron_file}")
            except Exception as e:
                logger.warning(f"Failed to load {cron_file}: {e}")
                # Continue processing other files
    
    except PermissionError:
        raise PermissionError(f"Permission denied accessing {cron_d_path}")
    except Exception as e:
        logger.warning(f"Error scanning {cron_d_path}: {e}")
    
    return jobs


def _load_cron_d_file(file_path: str) -> List[CronJob]:
    """
    Load cron jobs from a single file in /etc/cron.d/.
    
    Args:
        file_path: Path to the cron.d file
        
    Returns:
        List of CronJob objects
        
    Raises:
        PermissionError: If file can't be read
        CronParseError: If file has critical parsing errors
    """
    jobs = []
    parse_errors = []
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            for line_number, line in enumerate(f, 1):
                try:
                    job = parse_cron_line(line.strip(), source="system", user=None)
                    if job:
                        jobs.append(job)
                        logger.debug(f"Parsed job from {file_path}:{line_number}: {job.raw_line}")
                except CronParseError as e:
                    error_msg = f"{file_path}:{line_number}: {e}"
                    logger.debug(error_msg)
                    parse_errors.append(error_msg)
    
    except PermissionError:
        raise PermissionError(f"Permission denied reading {file_path}")
    except UnicodeDecodeError as e:
        raise CronParseError(f"File encoding error in {file_path}: {e}")
    except Exception as e:
        raise CronParseError(f"Error reading {file_path}: {e}")
    
    if parse_errors:
        logger.debug(f"Encountered {len(parse_errors)} parsing errors in {file_path}")
    
    return jobs


def _get_mock_system_crontab_data() -> List[CronJob]:
    """
    Provide mock system crontab data for Windows development.
    
    Returns:
        List of mock system CronJob objects
    """
    mock_system_lines = [
        "# System crontab for mock testing",
        "SHELL=/bin/bash",
        "PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin",
        "",
        "# Example /etc/crontab entries",
        "17 * * * * root cd / && run-parts --report /etc/cron.hourly",
        "25 6 * * * root test -x /usr/sbin/anacron || ( cd / && run-parts --report /etc/cron.daily )",
        "47 6 * * 7 root test -x /usr/sbin/anacron || ( cd / && run-parts --report /etc/cron.weekly )",
        "52 6 1 * * root test -x /usr/sbin/anacron || ( cd / && run-parts --report /etc/cron.monthly )",
        "",
        "# Example /etc/cron.d/ entries",
        "0 2 * * * backup /usr/local/bin/system-backup.sh",
        "*/10 * * * * monitor /usr/local/bin/check-services.sh",
        "30 3 * * 0 admin /usr/local/bin/weekly-maintenance.sh",
    ]
    
    cron_jobs = []
    for line in mock_system_lines:
        try:
            cron_job = parse_cron_line(line, source="system", user=None)
            if cron_job:
                cron_jobs.append(cron_job)
        except CronParseError as e:
            logger.debug(f"Mock system data parse error: {e}")
    
    return cron_jobs


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