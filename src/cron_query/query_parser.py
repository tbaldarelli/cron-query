#!/usr/bin/env python3
"""
Query Parser - Handles natural language query parsing for cron schedules.

This module converts natural language queries into structured criteria
that can be used to filter and analyze cron jobs.
"""

import logging
import re
from dataclasses import dataclass
from typing import List, Optional, Set, Union, Tuple
from enum import Enum
from datetime import datetime, timedelta


logger = logging.getLogger(__name__)


class QueryType(Enum):
    """Types of queries supported by the parser."""
    DAY_BASED = "day_based"
    TIME_BASED = "time_based" 
    COMBINED = "combined"
    UNKNOWN = "unknown"


@dataclass
class QueryCriteria:
    """
    Represents parsed query criteria for filtering cron jobs.
    
    Attributes:
        query_type: Type of query (day_based, time_based, combined)
        days_of_week: Set of day numbers (0=Sunday, 1=Monday, ..., 6=Saturday)
        time_hour: Hour for time-based queries (0-23)
        time_minute: Minute for time-based queries (0-59)
        raw_query: Original query string for reference
        is_specific_date: Whether this is for a specific date vs recurring
        weekdays_only: True if query is for weekdays (Mon-Fri)
        weekends_only: True if query is for weekends (Sat-Sun)
        specific_date: Specific date for "this Saturday" style queries
        time_range_start: Start time for range queries ("after 10 AM")
        time_range_end: End time for range queries ("before 5 PM")
        is_time_after: True for "after X" queries
        is_time_before: True for "before X" queries  
        is_time_between: True for "between X and Y" queries
    """
    query_type: QueryType
    raw_query: str
    days_of_week: Optional[Set[int]] = None
    time_hour: Optional[int] = None
    time_minute: Optional[int] = None
    is_specific_date: bool = False
    weekdays_only: bool = False
    weekends_only: bool = False
    specific_date: Optional[datetime] = None
    time_range_start: Optional[Tuple[int, int]] = None  # (hour, minute)
    time_range_end: Optional[Tuple[int, int]] = None    # (hour, minute)
    is_time_after: bool = False
    is_time_before: bool = False
    is_time_between: bool = False
    
    def __post_init__(self):
        """Validate the criteria after initialization."""
        if self.time_hour is not None and not (0 <= self.time_hour <= 23):
            raise ValueError(f"Invalid hour: {self.time_hour} (must be 0-23)")
        if self.time_minute is not None and not (0 <= self.time_minute <= 59):
            raise ValueError(f"Invalid minute: {self.time_minute} (must be 0-59)")
        if self.days_of_week is not None:
            for day in self.days_of_week:
                if not (0 <= day <= 6):
                    raise ValueError(f"Invalid day of week: {day} (must be 0-6)")


# Day name mappings (case-insensitive)
DAY_NAMES = {
    'sunday': 0, 'sun': 0,
    'monday': 1, 'mon': 1,
    'tuesday': 2, 'tue': 2, 'tues': 2,
    'wednesday': 3, 'wed': 3,
    'thursday': 4, 'thu': 4, 'thur': 4, 'thurs': 4,
    'friday': 5, 'fri': 5,
    'saturday': 6, 'sat': 6
}

# Special day groupings
WEEKDAYS = {1, 2, 3, 4, 5}  # Monday-Friday
WEEKENDS = {0, 6}  # Saturday, Sunday


class QueryParseError(Exception):
    """Exception raised when parsing a query fails."""
    pass


def parse_query(query: str) -> QueryCriteria:
    """
    Parse a natural language query into structured criteria.
    
    Args:
        query: Natural language query string
        
    Returns:
        QueryCriteria object representing the parsed query
        
    Raises:
        QueryParseError: If the query cannot be parsed
    """
    if not query or not query.strip():
        raise QueryParseError("Empty query")
    
    query = query.strip().lower()
    logger.debug(f"Parsing query: '{query}'")
    
    # Try to parse as combined query first (day + time)
    try:
        combined_criteria = parse_combined_query(query)
        if combined_criteria:
            logger.debug(f"Parsed as combined query: {combined_criteria}")
            return combined_criteria
    except QueryParseError:
        pass
    
    # Try to parse as day-based query
    try:
        day_criteria = parse_day_query(query)
        if day_criteria:
            logger.debug(f"Parsed as day query: {day_criteria}")
            return day_criteria
    except QueryParseError:
        pass
    
    # Try to parse as time-based query (including ranges)
    try:
        time_criteria = parse_time_query(query)
        if time_criteria:
            logger.debug(f"Parsed as time query: {time_criteria}")
            return time_criteria
    except QueryParseError:
        pass
    
    # If we can't parse it, return unknown type
    logger.warning(f"Could not parse query: '{query}'")
    return QueryCriteria(
        query_type=QueryType.UNKNOWN,
        raw_query=query
    )


def parse_combined_query(query: str) -> Optional[QueryCriteria]:
    """
    Parse combined day + time queries like 'this Saturday after 10 AM'.
    
    Args:
        query: Query string to parse
        
    Returns:
        QueryCriteria for combined queries, or None if not a combined query
        
    Raises:
        QueryParseError: If query appears to be combined but invalid
    """
    query = query.strip().lower()
    query = _normalize_query(query)
    
    # Look for patterns that combine days and time constraints
    combined_patterns = [
        # "this Saturday after 10 AM" or "this Saturday, after 10 AM" (including typos like "comming")
        r'(this|next|coming|comming)\s+(\w+)\s*,?\s*(after|before|between)\s+(.+)',
        # "Saturday after 10 AM" or "Saturday, after 10 AM"
        r'(\w+day|weekends?|weekdays?)\s*,?\s*(after|before|between)\s+(.+)',
        # "after 10 AM on Saturday"
        r'(after|before|between)\s+(.+)\s+on\s+(\w+day|weekends?|weekdays?)',
    ]
    
    for pattern in combined_patterns:
        match = re.search(pattern, query)
        if match:
            return _parse_combined_match(query, match, pattern)
    
    return None


def _parse_combined_match(query: str, match, pattern: str) -> QueryCriteria:
    """
    Parse a matched combined query pattern.
    """
    groups = match.groups()
    
    # Initialize base criteria
    criteria = QueryCriteria(query_type=QueryType.COMBINED, raw_query=query)
    
    # Pattern-specific parsing
    if 'this|next|coming' in pattern:
        # "this Saturday after 10 AM"
        time_modifier, day_name, time_relation, time_part = groups
        
        # Parse the day part
        day_criteria = parse_day_query(f"{time_modifier} {day_name}")
        if day_criteria:
            criteria.days_of_week = day_criteria.days_of_week
            criteria.is_specific_date = day_criteria.is_specific_date
            criteria.specific_date = day_criteria.specific_date
            criteria.weekdays_only = day_criteria.weekdays_only
            criteria.weekends_only = day_criteria.weekends_only
        
        # Parse the time part - handle ranges directly
        if time_relation == 'after':
            time_info = _parse_single_time(time_part)
            if time_info:
                criteria.time_range_start = time_info
                criteria.is_time_after = True
        elif time_relation == 'before':
            time_info = _parse_single_time(time_part)
            if time_info:
                criteria.time_range_end = time_info
                criteria.is_time_before = True
        elif time_relation == 'between':
            # Handle "between X and Y" in time_part
            between_match = re.search(r'([^\s]+(?:\s*(?:am|pm))?)\s+and\s+([^\s]+(?:\s*(?:am|pm))?)', time_part)
            if between_match:
                start_str, end_str = between_match.groups()
                start_info = _parse_single_time(start_str)
                end_info = _parse_single_time(end_str)
                if start_info and end_info:
                    criteria.time_range_start = start_info
                    criteria.time_range_end = end_info
                    criteria.is_time_between = True
    
    elif 'on\\s+' in pattern:
        # "after 10 AM on Saturday"
        time_relation, time_part, day_name = groups
        
        # Parse the day part
        day_criteria = parse_day_query(day_name)
        if day_criteria:
            criteria.days_of_week = day_criteria.days_of_week
            criteria.weekdays_only = day_criteria.weekdays_only
            criteria.weekends_only = day_criteria.weekends_only
        
        # Parse the time part - handle ranges directly
        if time_relation == 'after':
            time_info = _parse_single_time(time_part)
            if time_info:
                criteria.time_range_start = time_info
                criteria.is_time_after = True
        elif time_relation == 'before':
            time_info = _parse_single_time(time_part)
            if time_info:
                criteria.time_range_end = time_info
                criteria.is_time_before = True
        elif time_relation == 'between':
            # Handle "between X and Y" in time_part
            between_match = re.search(r'([^\s]+(?:\s*(?:am|pm))?)\s+and\s+([^\s]+(?:\s*(?:am|pm))?)', time_part)
            if between_match:
                start_str, end_str = between_match.groups()
                start_info = _parse_single_time(start_str)
                end_info = _parse_single_time(end_str)
                if start_info and end_info:
                    criteria.time_range_start = start_info
                    criteria.time_range_end = end_info
                    criteria.is_time_between = True
    
    else:
        # "Saturday after 10 AM"
        day_name, time_relation, time_part = groups
        
        # Parse the day part
        day_criteria = parse_day_query(day_name)
        if day_criteria:
            criteria.days_of_week = day_criteria.days_of_week
            criteria.weekdays_only = day_criteria.weekdays_only
            criteria.weekends_only = day_criteria.weekends_only
        
        # Parse the time part - handle ranges directly
        if time_relation == 'after':
            time_info = _parse_single_time(time_part)
            if time_info:
                criteria.time_range_start = time_info
                criteria.is_time_after = True
        elif time_relation == 'before':
            time_info = _parse_single_time(time_part)
            if time_info:
                criteria.time_range_end = time_info
                criteria.is_time_before = True
        elif time_relation == 'between':
            # Handle "between X and Y" in time_part
            between_match = re.search(r'([^\s]+(?:\s*(?:am|pm))?)\s+and\s+([^\s]+(?:\s*(?:am|pm))?)', time_part)
            if between_match:
                start_str, end_str = between_match.groups()
                start_info = _parse_single_time(start_str)
                end_info = _parse_single_time(end_str)
                if start_info and end_info:
                    criteria.time_range_start = start_info
                    criteria.time_range_end = end_info
                    criteria.is_time_between = True
    
    return criteria


def parse_day_query(query: str) -> Optional[QueryCriteria]:
    """
    Parse day-based queries like 'Saturday', 'weekdays', 'weekends'.
    
    Args:
        query: Query string to parse
        
    Returns:
        QueryCriteria for day-based queries, or None if not a day query
        
    Raises:
        QueryParseError: If query appears to be day-based but invalid
    """
    query = query.strip().lower()
    
    # Remove common prefixes that don't affect meaning
    query = _normalize_query(query)
    
    # Check for basic time references (today, tomorrow, yesterday)
    basic_time_refs = ['today', 'tomorrow', 'yesterday']
    for time_ref in basic_time_refs:
        if query == time_ref or query == f'jobs {time_ref}':
            today = datetime.now()
            if time_ref == 'yesterday':
                target_date = today - timedelta(days=1)
            elif time_ref == 'tomorrow':
                target_date = today + timedelta(days=1)
            else:  # today
                target_date = today
            
            return QueryCriteria(
                query_type=QueryType.DAY_BASED,
                raw_query=query,
                is_specific_date=True,
                specific_date=target_date.replace(hour=0, minute=0, second=0, microsecond=0)
            )

    # Check for weekdays/weekends
    if 'weekday' in query or 'week day' in query:
        return QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query=query,
            days_of_week=WEEKDAYS.copy(),
            weekdays_only=True
        )
    
    if 'weekend' in query or 'week end' in query:
        return QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query=query,
            days_of_week=WEEKENDS.copy(),
            weekends_only=True
        )
    
    # Check for specific day names
    found_days = set()
    query_words = query.split()
    
    for word in query_words:
        # Remove punctuation
        clean_word = re.sub(r'[^\w]', '', word)
        if clean_word in DAY_NAMES:
            found_days.add(DAY_NAMES[clean_word])
    
    if found_days:
        # Check if this is a specific date query (e.g., "this Saturday")
        # Handle common typos like "comming" for "coming"
        is_specific = any(word in query for word in ['this', 'next', 'coming', 'comming'])
        specific_date = None
        
        if is_specific:
            # Calculate the specific date
            specific_date = _calculate_relative_date(query, found_days)
        
        return QueryCriteria(
            query_type=QueryType.DAY_BASED,
            raw_query=query,
            days_of_week=found_days,
            is_specific_date=is_specific,
            specific_date=specific_date
        )
    
    # Check if query contains day-related words but we couldn't parse it
    day_keywords = ['day', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']
    if any(keyword in query for keyword in day_keywords):
        # This looks like a day query but we couldn't parse it
        raise QueryParseError(f"Could not parse day query: '{query}'")
    
    return None


def parse_time_query(query: str) -> Optional[QueryCriteria]:
    """
    Parse time-based queries like '8 AM', '8:30 PM', '20:30'.
    
    Args:
        query: Query string to parse
        
    Returns:
        QueryCriteria for time-based queries, or None if not a time query
        
    Raises:
        QueryParseError: If query appears to be time-based but invalid
    """
    query = query.strip().lower()
    
    # Remove common prefixes that don't affect meaning
    query = _normalize_query(query)
    
    # Check for time range patterns first
    time_range_result = _parse_time_ranges(query)
    if time_range_result:
        return time_range_result
    
    # Pattern 1: "8 AM", "8 PM", "8:30 AM", "8:30 PM"
    am_pm_pattern = r'\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b'
    match = re.search(am_pm_pattern, query)
    
    if match:
        hour_str, minute_str, am_pm = match.groups()
        hour = int(hour_str)
        minute = int(minute_str) if minute_str else 0
        
        # Validate hour for 12-hour format
        if hour < 1 or hour > 12:
            raise QueryParseError(f"Invalid hour for 12-hour format: {hour} (must be 1-12)")
        
        # Convert to 24-hour format
        if am_pm == 'am':
            if hour == 12:
                hour = 0  # 12 AM = 0:00
        else:  # pm
            if hour != 12:
                hour += 12  # 1 PM = 13:00, but 12 PM = 12:00
        
        return QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query=query,
            time_hour=hour,
            time_minute=minute
        )
    
    # Pattern 2: "20:30", "08:15", "9:00" (24-hour format)
    twenty_four_pattern = r'\b(\d{1,2}):(\d{2})\b'
    match = re.search(twenty_four_pattern, query)
    
    if match:
        hour_str, minute_str = match.groups()
        hour = int(hour_str)
        minute = int(minute_str)
        
        # Validate 24-hour format
        if hour < 0 or hour > 23:
            raise QueryParseError(f"Invalid hour for 24-hour format: {hour} (must be 0-23)")
        if minute < 0 or minute > 59:
            raise QueryParseError(f"Invalid minute: {minute} (must be 0-59)")
        
        return QueryCriteria(
            query_type=QueryType.TIME_BASED,
            raw_query=query,
            time_hour=hour,
            time_minute=minute
        )
    
    # Pattern 3: Just hour with no minutes "at 8", "8 o'clock"
    if not re.search(r'(am|pm|:)', query):  # Only if no AM/PM or colon present
        # Look for "at [number]" pattern
        at_pattern = r'\bat\s+(\d{1,2})\b'
        match = re.search(at_pattern, query)
        if match:
            hour_str = match.group(1)
            hour = int(hour_str)
            
            # Assume 24-hour format for plain numbers
            if hour < 0 or hour > 23:
                raise QueryParseError(f"Invalid hour: {hour} (must be 0-23)")
            
            return QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query=query,
                time_hour=hour,
                time_minute=0
            )
        
        # Look for "[number] o'clock" pattern
        oclock_pattern = r'\b(\d{1,2})\s+o\'?clock\b'
        match = re.search(oclock_pattern, query)
        if match:
            hour_str = match.group(1)
            hour = int(hour_str)
            
            # Assume 24-hour format for plain numbers
            if hour < 0 or hour > 23:
                raise QueryParseError(f"Invalid hour: {hour} (must be 0-23)")
            
            return QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query=query,
                time_hour=hour,
                time_minute=0
            )
    
    # Special case: check for standalone 'at' with digits for time queries
    if re.search(r'\bat\s+\d', query):
        # This looks like "at [number]" but we couldn't parse it
        raise QueryParseError(f"Could not parse time query: '{query}'")
    
    # Check if query contains time-related words but we couldn't parse it
    time_keywords = ['am', 'pm', 'oclock', "o'clock", 'time', ':', 'hour', 'minute', 'after', 'before', 'between']
    if any(keyword in query for keyword in time_keywords):
        # This looks like a time query but we couldn't parse it
        raise QueryParseError(f"Could not parse time query: '{query}'")
    
    return None


def _parse_time_ranges(query: str) -> Optional[QueryCriteria]:
    """
    Parse time range queries like 'after 10 AM', 'before 5 PM', 'between 9 AM and 5 PM'.
    
    Args:
        query: Query string to parse
        
    Returns:
        QueryCriteria for time range queries, or None if not a time range query
    """
    # Pattern 1: "after 10 AM", "after 10:30 PM"
    after_pattern = r'after\s+([^\s]+(?:\s*(?:am|pm))?(?:\s*and\s*[^\s]+)?)'  
    match = re.search(after_pattern, query)
    if match:
        time_str = match.group(1)
        time_info = _parse_single_time(time_str)
        if time_info:
            hour, minute = time_info
            return QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query=query,
                time_range_start=(hour, minute),
                is_time_after=True
            )
    
    # Pattern 2: "before 5 PM", "before 17:30"
    before_pattern = r'before\s+([^\s]+(?:\s*(?:am|pm))?(?:\s*and\s*[^\s]+)?)'  
    match = re.search(before_pattern, query)
    if match:
        time_str = match.group(1)
        time_info = _parse_single_time(time_str)
        if time_info:
            hour, minute = time_info
            return QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query=query,
                time_range_end=(hour, minute),
                is_time_before=True
            )
    
    # Pattern 3: "between 9 AM and 5 PM"
    between_pattern = r'between\s+([^\s]+(?:\s*(?:am|pm))?)\s+and\s+([^\s]+(?:\s*(?:am|pm))?)'  
    match = re.search(between_pattern, query)
    if match:
        start_time_str, end_time_str = match.groups()
        start_info = _parse_single_time(start_time_str)
        end_info = _parse_single_time(end_time_str)
        
        if start_info and end_info:
            start_hour, start_minute = start_info
            end_hour, end_minute = end_info
            return QueryCriteria(
                query_type=QueryType.TIME_BASED,
                raw_query=query,
                time_range_start=(start_hour, start_minute),
                time_range_end=(end_hour, end_minute),
                is_time_between=True
            )
    
    return None


def _parse_single_time(time_str: str) -> Optional[Tuple[int, int]]:
    """
    Parse a single time string without recursion.
    
    Args:
        time_str: Time string like "10 AM", "5:30 PM", "17:30"
        
    Returns:
        Tuple of (hour, minute) or None if parsing fails
    """
    time_str = time_str.strip().lower()
    
    # Pattern 1: "8 AM", "8 PM", "8:30 AM", "8:30 PM"
    am_pm_pattern = r'\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b'
    match = re.search(am_pm_pattern, time_str)
    
    if match:
        hour_str, minute_str, am_pm = match.groups()
        hour = int(hour_str)
        minute = int(minute_str) if minute_str else 0
        
        # Validate hour for 12-hour format
        if hour < 1 or hour > 12:
            return None
        
        # Convert to 24-hour format
        if am_pm == 'am':
            if hour == 12:
                hour = 0  # 12 AM = 0:00
        else:  # pm
            if hour != 12:
                hour += 12  # 1 PM = 13:00, but 12 PM = 12:00
        
        return (hour, minute)
    
    # Pattern 2: "20:30", "08:15", "9:00" (24-hour format)
    twenty_four_pattern = r'\b(\d{1,2}):(\d{2})\b'
    match = re.search(twenty_four_pattern, time_str)
    
    if match:
        hour_str, minute_str = match.groups()
        hour = int(hour_str)
        minute = int(minute_str)
        
        # Validate 24-hour format
        if hour < 0 or hour > 23 or minute < 0 or minute > 59:
            return None
        
        return (hour, minute)
    
    return None


def _calculate_relative_date(query: str, day_nums: Set[int]) -> Optional[datetime]:
    """
    Calculate the specific date for relative queries like 'this Saturday', 'next Monday'.
    
    Args:
        query: Original query string
        day_nums: Set of target day numbers (0=Sunday, 1=Monday, etc.)
        
    Returns:
        Calculated datetime, or None if calculation fails
    """
    if not day_nums:
        return None
        
    # For simplicity, take the first day if multiple specified
    target_day = next(iter(day_nums))
    
    now = datetime.now()
    current_weekday = now.weekday()  # Python: 0=Monday, 6=Sunday
    
    # Convert target from cron format (0=Sunday) to Python format (0=Monday)
    if target_day == 0:  # Sunday in cron -> 6 in Python
        python_target = 6
    else:  # Monday=1 -> 0, Tuesday=2 -> 1, etc.
        python_target = target_day - 1
    
    if 'this' in query or 'coming' in query or 'comming' in query:
        # "This Saturday" or "coming Saturday" - find next occurrence  
        # Handle "comming" typo as "coming"
        days_ahead = (python_target - current_weekday) % 7
        if days_ahead == 0:  # Today is the target day
            days_ahead = 0 if 'this' in query else 7  # "this" includes today, "coming" means next week
        target_date = now + timedelta(days=days_ahead)
        
    elif 'next' in query:
        # "Next Saturday" - find the Saturday after this coming Saturday
        days_ahead = (python_target - current_weekday) % 7
        if days_ahead == 0:
            days_ahead = 7  # Next week's occurrence
        else:
            days_ahead += 7  # Skip this week's occurrence
        target_date = now + timedelta(days=days_ahead)
    else:
        # Default to "this" behavior for unspecified relative queries
        days_ahead = (python_target - current_weekday) % 7
        target_date = now + timedelta(days=days_ahead)
    
    return target_date.replace(hour=0, minute=0, second=0, microsecond=0)


def _normalize_query(query: str) -> str:
    """
    Normalize query by removing common prefixes and words that don't affect meaning.
    
    Args:
        query: Original query string
        
    Returns:
        Normalized query string
    """
    # Remove common question prefixes
    prefixes_to_remove = [
        'which jobs run',
        'what jobs run', 
        'which jobs execute',
        'what jobs execute',
        'show me jobs that run',
        'show jobs that run',
        'find jobs that run',
        'jobs that run'
    ]
    
    normalized = query.strip().lower()
    
    for prefix in prefixes_to_remove:
        if normalized.startswith(prefix):
            normalized = normalized[len(prefix):].strip()
            break
    
    # Remove common prepositions and articles that don't affect meaning
    # But preserve 'at' if followed by a number (time expression)
    words_to_remove = ['on', 'in', 'the', 'a', 'an', 'during']
    words = normalized.split()
    
    # Only remove these words if they're at the beginning
    while words and words[0] in words_to_remove:
        words.pop(0)
    
    # Special case: remove 'at' only if NOT followed by a number
    if words and words[0] == 'at':
        if len(words) < 2 or not re.match(r'\d', words[1]):
            words.pop(0)
    
    return ' '.join(words)


def format_criteria_description(criteria: QueryCriteria) -> str:
    """
    Generate a human-readable description of the query criteria.
    
    Args:
        criteria: QueryCriteria object to describe
        
    Returns:
        Human-readable description string
    """
    if criteria.query_type == QueryType.DAY_BASED:
        if criteria.weekdays_only:
            return "weekdays (Monday-Friday)"
        elif criteria.weekends_only:
            return "weekends (Saturday-Sunday)"
        elif criteria.days_of_week:
            day_names = []
            day_name_map = {v: k.title() for k, v in DAY_NAMES.items() if len(k) > 3}  # Use full names
            for day_num in sorted(criteria.days_of_week):
                for name, num in DAY_NAMES.items():
                    if num == day_num and len(name) > 3:  # Use full name
                        day_names.append(name.title())
                        break
            
            if len(day_names) == 1:
                return f"{day_names[0]}"
            elif len(day_names) == 2:
                return f"{day_names[0]} and {day_names[1]}"
            else:
                return f"{', '.join(day_names[:-1])}, and {day_names[-1]}"
    
    elif criteria.query_type == QueryType.TIME_BASED:
        # Handle time ranges
        if criteria.is_time_after and criteria.time_range_start:
            start_hour, start_minute = criteria.time_range_start
            time_str = _format_time(start_hour, start_minute)
            return f"after {time_str}"
        elif criteria.is_time_before and criteria.time_range_end:
            end_hour, end_minute = criteria.time_range_end
            time_str = _format_time(end_hour, end_minute)
            return f"before {time_str}"
        elif criteria.is_time_between and criteria.time_range_start and criteria.time_range_end:
            start_hour, start_minute = criteria.time_range_start
            end_hour, end_minute = criteria.time_range_end
            start_str = _format_time(start_hour, start_minute)
            end_str = _format_time(end_hour, end_minute)
            return f"between {start_str} and {end_str}"
        
        # Handle specific times
        hour = criteria.time_hour
        minute = criteria.time_minute
        
        if hour is not None:
            return _format_time(hour, minute or 0)
    
    elif criteria.query_type == QueryType.COMBINED:
        parts = []
        
        # Day part
        if criteria.specific_date:
            day_str = criteria.specific_date.strftime("%A, %B %d")
            parts.append(day_str)
        elif criteria.weekdays_only:
            parts.append("weekdays")
        elif criteria.weekends_only:
            parts.append("weekends")
        elif criteria.days_of_week:
            day_names = []
            for day_num in sorted(criteria.days_of_week):
                for name, num in DAY_NAMES.items():
                    if num == day_num and len(name) > 3:
                        day_names.append(name.title())
                        break
            if day_names:
                parts.append(", ".join(day_names))
        
        # Time part
        if criteria.is_time_after and criteria.time_range_start:
            start_hour, start_minute = criteria.time_range_start
            time_str = _format_time(start_hour, start_minute)
            parts.append(f"after {time_str}")
        elif criteria.is_time_before and criteria.time_range_end:
            end_hour, end_minute = criteria.time_range_end
            time_str = _format_time(end_hour, end_minute)
            parts.append(f"before {time_str}")
        elif criteria.is_time_between and criteria.time_range_start and criteria.time_range_end:
            start_hour, start_minute = criteria.time_range_start
            end_hour, end_minute = criteria.time_range_end
            start_str = _format_time(start_hour, start_minute)
            end_str = _format_time(end_hour, end_minute)
            parts.append(f"between {start_str} and {end_str}")
        elif criteria.time_hour is not None:
            time_str = _format_time(criteria.time_hour, criteria.time_minute or 0)
            parts.append(f"at {time_str}")
        
        if parts:
            return " ".join(parts)
    
    elif criteria.query_type == QueryType.UNKNOWN:
        return f"unknown query: '{criteria.raw_query}'"
    
    return f"query: '{criteria.raw_query}'"


def _format_time(hour: int, minute: int) -> str:
    """
    Format time in a human-readable 12-hour format.
    
    Args:
        hour: Hour (0-23)
        minute: Minute (0-59)
        
    Returns:
        Formatted time string
    """
    if minute == 0:
        if hour == 0:
            return "midnight (12:00 AM)"
        elif hour == 12:
            return "noon (12:00 PM)"
        elif hour < 12:
            return f"{hour}:00 AM"
        else:
            return f"{hour-12}:00 PM"
    else:
        if hour == 0:
            return f"12:{minute:02d} AM"
        elif hour < 12:
            return f"{hour}:{minute:02d} AM"
        elif hour == 12:
            return f"12:{minute:02d} PM"
        else:
            return f"{hour-12}:{minute:02d} PM"
