#!/usr/bin/env python3
"""
Schedule Analyzer - Core logic for matching cron jobs against query criteria.

This module provides the core functionality to analyze cron job schedules
and determine which jobs match specific query criteria using croniter.
"""

import logging
from datetime import datetime, timedelta
from typing import List, Set, Optional, Tuple, Iterator
from croniter import croniter

from .cron_loader import CronJob
from .query_parser import QueryCriteria, QueryType


logger = logging.getLogger(__name__)


class ScheduleAnalysisError(Exception):
    """Exception raised when schedule analysis fails."""
    pass


def find_matching_jobs(cron_jobs: List[CronJob], criteria: QueryCriteria) -> List[CronJob]:
    """
    Find cron jobs that match the given query criteria.
    
    Args:
        cron_jobs: List of cron jobs to analyze
        criteria: Query criteria to match against
        
    Returns:
        List of matching cron jobs
        
    Raises:
        ScheduleAnalysisError: If analysis fails
    """
    logger.debug(f"Analyzing {len(cron_jobs)} jobs against criteria: {criteria}")
    
    if criteria.query_type == QueryType.UNKNOWN:
        logger.warning("Cannot analyze unknown query type")
        return []
    
    matching_jobs = []
    
    for job in cron_jobs:
        try:
            if matches_criteria(job, criteria):
                matching_jobs.append(job)
                logger.debug(f"Job matches: {job.raw_line}")
        except Exception as e:
            logger.warning(f"Error analyzing job '{job.raw_line}': {e}")
            continue
    
    logger.info(f"Found {len(matching_jobs)} matching jobs out of {len(cron_jobs)}")
    return matching_jobs


def matches_criteria(job: CronJob, criteria: QueryCriteria) -> bool:
    """
    Check if a cron job matches the given criteria.
    
    Args:
        job: Cron job to check
        criteria: Query criteria to match against
        
    Returns:
        True if job matches criteria, False otherwise
        
    Raises:
        ScheduleAnalysisError: If analysis fails
    """
    if not job.is_valid:
        logger.debug(f"Skipping invalid job: {job.raw_line}")
        return False
    
    try:
        if criteria.query_type == QueryType.DAY_BASED:
            if criteria.is_specific_date and criteria.specific_date:
                # For specific dates like "this Saturday", check if job runs on that exact date
                return runs_on_specific_date(job, criteria.specific_date)
            else:
                # Regular day-of-week matching
                return runs_on_day_of_week(job, criteria.days_of_week)
        elif criteria.query_type == QueryType.TIME_BASED:
            # Handle time ranges
            if criteria.is_time_after or criteria.is_time_before or criteria.is_time_between:
                return runs_in_time_range(job, criteria)
            else:
                # Regular specific time matching
                return runs_at_time(job, criteria.time_hour, criteria.time_minute)
        elif criteria.query_type == QueryType.COMBINED:
            # Combined day and time queries with advanced features
            day_match = False
            if criteria.is_specific_date and criteria.specific_date:
                day_match = runs_on_specific_date(job, criteria.specific_date)
            elif criteria.days_of_week:
                day_match = runs_on_day_of_week(job, criteria.days_of_week)
            elif criteria.weekdays_only or criteria.weekends_only:
                day_match = runs_on_day_of_week(job, criteria.days_of_week)
            
            time_match = False
            if criteria.is_time_after or criteria.is_time_before or criteria.is_time_between:
                time_match = runs_in_time_range(job, criteria)
            elif criteria.time_hour is not None:
                time_match = runs_at_time(job, criteria.time_hour, criteria.time_minute)
            
            return day_match and time_match
        else:
            logger.warning(f"Unknown query type: {criteria.query_type}")
            return False
            
    except Exception as e:
        raise ScheduleAnalysisError(f"Failed to analyze job '{job.raw_line}': {e}")


def runs_on_day_of_week(job: CronJob, target_days: Optional[Set[int]]) -> bool:
    """
    Check if a cron job runs on any of the specified days of the week.
    
    This handles the complex cron logic where day-of-month AND day-of-week
    fields work with OR logic (not AND).
    
    Args:
        job: Cron job to check
        target_days: Set of day numbers (0=Sunday, 1=Monday, ..., 6=Saturday)
        
    Returns:
        True if job runs on any target day, False otherwise
        
    Raises:
        ScheduleAnalysisError: If analysis fails
    """
    if not target_days:
        return False
    
    try:
        # Create croniter instance for analysis
        cron = croniter(job.cron_expression)
        
        # Special case: if both day-of-month and day-of-week are specified (not *),
        # then cron uses OR logic - the job runs when EITHER condition is met
        dom_specified = job.day_of_month != '*'
        dow_specified = job.day_of_week != '*'
        
        if dom_specified and dow_specified:
            # Complex case: both day-of-month and day-of-week specified
            return _check_complex_day_logic(job, target_days, cron)
        elif dow_specified:
            # Simple case: only day-of-week specified
            return _check_day_of_week_field(job.day_of_week, target_days)
        elif dom_specified:
            # Day-of-month only: these jobs don't match day-of-week queries
            # They run on specific dates, not specific days of the week
            return False
        else:
            # Both are * - runs every day, so matches any target days
            return True
            
    except Exception as e:
        raise ScheduleAnalysisError(f"Failed to check day-of-week for job '{job.raw_line}': {e}")


def runs_at_time(job: CronJob, target_hour: Optional[int], target_minute: Optional[int]) -> bool:
    """
    Check if a cron job runs at the specified time.
    
    Args:
        job: Cron job to check
        target_hour: Target hour (0-23), None to ignore
        target_minute: Target minute (0-59), None to ignore
        
    Returns:
        True if job runs at target time, False otherwise
        
    Raises:
        ScheduleAnalysisError: If analysis fails
    """
    if target_hour is None and target_minute is None:
        return True  # No time criteria specified
    
    try:
        # Parse the hour and minute fields
        hour_match = _matches_time_field(job.hour, target_hour) if target_hour is not None else True
        minute_match = _matches_time_field(job.minute, target_minute) if target_minute is not None else True
        
        return hour_match and minute_match
        
    except Exception as e:
        raise ScheduleAnalysisError(f"Failed to check time for job '{job.raw_line}': {e}")


def get_next_runs(job: CronJob, count: int = 5, start_time: Optional[datetime] = None) -> List[datetime]:
    """
    Get the next scheduled run times for a cron job.
    
    Args:
        job: Cron job to analyze
        count: Number of next runs to return
        start_time: Starting time for calculation (default: now)
        
    Returns:
        List of next scheduled datetime objects
        
    Raises:
        ScheduleAnalysisError: If analysis fails
    """
    if not job.is_valid:
        raise ScheduleAnalysisError(f"Invalid cron expression: {job.cron_expression}")
    
    try:
        if start_time is None:
            start_time = datetime.now()
        
        cron = croniter(job.cron_expression, start_time)
        next_runs = []
        
        for _ in range(count):
            next_run = cron.get_next(datetime)
            next_runs.append(next_run)
        
        return next_runs
        
    except Exception as e:
        raise ScheduleAnalysisError(f"Failed to calculate next runs for job '{job.raw_line}': {e}")


def runs_on_specific_date(job: CronJob, target_date: datetime) -> bool:
    """
    Check if a cron job runs on a specific date.
    
    Args:
        job: Cron job to check
        target_date: Specific date to check
        
    Returns:
        True if job runs on the target date, False otherwise
        
    Raises:
        ScheduleAnalysisError: If analysis fails
    """
    if not job.is_valid:
        return False
    
    try:
        # Create croniter starting from the target date
        start_of_day = target_date.replace(hour=0, minute=0, second=0, microsecond=0)
        end_of_day = start_of_day + timedelta(days=1)
        
        cron = croniter(job.cron_expression, start_of_day)
        
        # Use a more efficient approach: check if the job would run at all on this date
        # We'll sample from one minute before the target day to the end of the target day
        pre_start = start_of_day - timedelta(minutes=1)
        cron = croniter(job.cron_expression, pre_start)
        
        # Check for runs within the target date
        for _ in range(24 * 60 + 2):  # Check beyond the day boundary
            try:
                next_run = cron.get_next(datetime)
                if next_run >= end_of_day:
                    break  # Past the target date, no more runs on this date
                if start_of_day <= next_run < end_of_day:
                    return True  # Found a run on the target date
            except:
                break  # Error getting next run
        
        return False
        
    except Exception as e:
        raise ScheduleAnalysisError(f"Failed to check specific date for job '{job.raw_line}': {e}")


def runs_in_time_range(job: CronJob, criteria) -> bool:
    """
    Check if a cron job runs within the specified time range.
    
    Args:
        job: Cron job to check
        criteria: Query criteria with time range information
        
    Returns:
        True if job runs within the time range, False otherwise
        
    Raises:
        ScheduleAnalysisError: If analysis fails
    """
    if not job.is_valid:
        return False
    
    try:
        # Parse the hour and minute fields to get all possible run times
        hour_values = _parse_cron_field(job.hour, 0, 23)
        minute_values = _parse_cron_field(job.minute, 0, 59)
        
        # Check each combination of hour and minute
        for hour in hour_values:
            for minute in minute_values:
                if _time_in_range(hour, minute, criteria):
                    return True
        
        return False
        
    except Exception as e:
        raise ScheduleAnalysisError(f"Failed to check time range for job '{job.raw_line}': {e}")


def _time_in_range(hour: int, minute: int, criteria) -> bool:
    """
    Check if a specific time falls within the criteria's time range.
    
    Args:
        hour: Hour (0-23)
        minute: Minute (0-59)
        criteria: Query criteria with time range information
        
    Returns:
        True if time is in range, False otherwise
    """
    current_minutes = hour * 60 + minute
    
    if criteria.is_time_after and criteria.time_range_start:
        start_hour, start_minute = criteria.time_range_start
        start_minutes = start_hour * 60 + start_minute
        return current_minutes > start_minutes
    
    elif criteria.is_time_before and criteria.time_range_end:
        end_hour, end_minute = criteria.time_range_end
        end_minutes = end_hour * 60 + end_minute
        return current_minutes < end_minutes
    
    elif criteria.is_time_between and criteria.time_range_start and criteria.time_range_end:
        start_hour, start_minute = criteria.time_range_start
        end_hour, end_minute = criteria.time_range_end
        start_minutes = start_hour * 60 + start_minute
        end_minutes = end_hour * 60 + end_minute
        
        if start_minutes <= end_minutes:
            # Normal range: 9 AM to 5 PM
            return start_minutes <= current_minutes <= end_minutes
        else:
            # Overnight range: 10 PM to 6 AM
            return current_minutes >= start_minutes or current_minutes <= end_minutes
    
    return False


def _check_complex_day_logic(job: CronJob, target_days: Set[int], cron: croniter) -> bool:
    """
    Handle complex day logic when both day-of-month and day-of-week are specified.
    
    In cron, when both DOM and DOW are specified, the job runs when EITHER
    condition is met (OR logic, not AND).
    """
    # Check if day-of-week field matches any target days
    dow_matches = _check_day_of_week_field(job.day_of_week, target_days)
    
    if dow_matches:
        return True
    
    # Check if day-of-month creates matches on target days
    return _check_day_of_month_matches(job, target_days, cron)


def _check_day_of_week_field(dow_field: str, target_days: Set[int]) -> bool:
    """
    Check if a day-of-week field matches any target days.
    
    Handles patterns like: 1, 1-5, 1,3,5, */2, etc.
    """
    if dow_field == '*':
        return True
    
    # Parse the day-of-week field to get all matching days
    matching_days = _parse_cron_field(dow_field, 0, 6)  # 0-6 for days of week
    
    # Convert Sunday from 7 to 0 if present (some cron variants use 7 for Sunday)
    matching_days = {0 if day == 7 else day for day in matching_days}
    
    # Check if any matching days overlap with target days
    return bool(matching_days & target_days)


def _check_day_of_month_matches(job: CronJob, target_days: Set[int], cron: croniter) -> bool:
    """
    Check if day-of-month specifications would result in runs on target days.
    
    This is complex because we need to simulate the cron schedule to see
    which days of the week the specified days-of-month fall on.
    """
    # Sample a few months to check if DOM matches create runs on target days
    # This is a heuristic - we can't check infinite future dates
    sample_start = datetime.now().replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    
    for month_offset in range(6):  # Check 6 months ahead
        month_start = sample_start + timedelta(days=30 * month_offset)
        month_cron = croniter(job.cron_expression, month_start)
        
        # Check next 35 runs in this time period (covers a month)
        for _ in range(35):
            try:
                next_run = month_cron.get_next(datetime)
                if next_run.weekday() + 1 in target_days or (next_run.weekday() == 6 and 0 in target_days):
                    # Convert Python weekday (0=Monday) to cron weekday (0=Sunday, 1=Monday)
                    cron_weekday = 0 if next_run.weekday() == 6 else next_run.weekday() + 1
                    if cron_weekday in target_days:
                        return True
            except:
                break
                
    return False


def _matches_time_field(cron_field: str, target_value: int) -> bool:
    """
    Check if a cron time field (hour/minute) matches a target value.
    
    Args:
        cron_field: Cron field value (e.g., "8", "*/2", "8-10", "8,10,12")
        target_value: Target value to check
        
    Returns:
        True if field matches target value
    """
    if cron_field == '*':
        return True
    
    # Determine the valid range based on context
    if target_value <= 23:  # Assume hour field
        min_val, max_val = 0, 23
    else:  # Assume minute field  
        min_val, max_val = 0, 59
    
    matching_values = _parse_cron_field(cron_field, min_val, max_val)
    return target_value in matching_values


def _parse_cron_field(field: str, min_val: int, max_val: int) -> Set[int]:
    """
    Parse a cron field and return set of matching values.
    
    Handles patterns like: 1, 1-5, 1,3,5, */2, 8-12/2, etc.
    
    Args:
        field: Cron field to parse
        min_val: Minimum valid value
        max_val: Maximum valid value
        
    Returns:
        Set of values that match the field pattern
    """
    values = set()
    
    # Split by comma for multiple values
    parts = field.split(',')
    
    for part in parts:
        part = part.strip()
        
        if '/' in part:
            # Handle step values like */2, 1-5/2
            range_part, step = part.split('/', 1)
            step = int(step)
            
            if range_part == '*':
                # */step - every step values in full range
                values.update(range(min_val, max_val + 1, step))
            elif '-' in range_part:
                # start-end/step
                start, end = range_part.split('-', 1)
                start, end = int(start), int(end)
                values.update(range(start, end + 1, step))
            else:
                # single_value/step - not standard cron, but handle gracefully
                start = int(range_part)
                values.update(range(start, max_val + 1, step))
                
        elif '-' in part:
            # Handle ranges like 1-5
            start, end = part.split('-', 1)
            start, end = int(start), int(end)
            values.update(range(start, end + 1))
            
        else:
            # Single value or wildcard
            if part == '*':
                # Wildcard - include all values in range
                values.update(range(min_val, max_val + 1))
            else:
                # Single numeric value
                values.add(int(part))
    
    # Handle Sunday conversion for day-of-week fields (7 -> 0)
    # This must be done before filtering to valid range
    if min_val == 0 and max_val == 6:  # Day-of-week field (0-6)
        values = {0 if v == 7 else v for v in values}
    
    # Filter to valid range
    return {v for v in values if min_val <= v <= max_val}


def get_job_schedule_description(job: CronJob) -> str:
    """
    Generate a human-readable description of when a cron job runs.
    
    Args:
        job: Cron job to describe
        
    Returns:
        Human-readable schedule description
    """
    if not job.is_valid:
        return "Invalid cron expression"
    
    try:
        # Get next few runs to understand the pattern
        next_runs = get_next_runs(job, 3)
        
        # Basic description based on cron fields
        desc_parts = []
        
        # Minute description
        if job.minute == '*':
            desc_parts.append("every minute")
        elif job.minute == '0':
            desc_parts.append("at the top of the hour")
        else:
            desc_parts.append(f"at minute {job.minute}")
        
        # Hour description
        if job.hour != '*':
            hour_vals = _parse_cron_field(job.hour, 0, 23)
            if len(hour_vals) == 1:
                hour = list(hour_vals)[0]
                if hour == 0:
                    desc_parts.append("midnight")
                elif hour == 12:
                    desc_parts.append("noon")
                else:
                    desc_parts.append(f"{hour:02d}:00")
        
        # Day description
        if job.day_of_month != '*' and job.day_of_week != '*':
            desc_parts.append("(complex day logic)")
        elif job.day_of_week != '*':
            desc_parts.append(f"on day-of-week {job.day_of_week}")
        elif job.day_of_month != '*':
            desc_parts.append(f"on day {job.day_of_month} of month")
        
        description = " ".join(desc_parts)
        
        # Add next run info
        if next_runs:
            next_run_str = next_runs[0].strftime("%Y-%m-%d %H:%M")
            description += f" (next: {next_run_str})"
        
        return description.capitalize()
        
    except Exception as e:
        return f"Error analyzing schedule: {e}"