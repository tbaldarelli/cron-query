# Cron-Query Tool - Technical Design

## Architecture Overview

### High-Level Flow
```
User Query → Query Parser → Cron Data Loader → Schedule Analyzer → Output Formatter
```

## Core Components

### 1. Query Parser (`query_parser.py`)
**Purpose**: Parse natural language queries into structured filter criteria

**Input**: Raw query string (e.g., "which jobs run on Saturday")
**Output**: Query object with parsed criteria

```python
class QueryCriteria:
    day_of_week: Optional[int]  # 0-6 (Monday-Sunday)
    hour: Optional[int]         # 0-23
    minute: Optional[int]       # 0-59
    specific_date: Optional[datetime.date]
    day_range: Optional[tuple]  # For "Monday through Friday"
    time_range: Optional[tuple] # For "9 AM to 5 PM"
```

**Key Functions**:
- `parse_day_query()` - Handle "Saturday", "weekdays", "weekends"
- `parse_time_query()` - Handle "8 AM", "8:30 PM", time ranges
- `parse_date_query()` - Handle "January 15th", "2025-01-15"

### 2. Cron Data Loader (`cron_loader.py`)
**Purpose**: Load and parse crontab data from various sources

**Sources**:
- User crontab (`crontab -l`)
- System crontab (`/etc/crontab`)
- Cron directories (`/etc/cron.d/*`)

```python
class CronJob:
    expression: str      # "0 8 * * 1-5"
    command: str         # "/path/to/script.sh"
    source: str          # "user" | "system" | "/etc/cron.d/backup"
    user: str           # Job owner
    croniter_obj: croniter  # For schedule calculations
```

**Key Functions**:
- `load_user_crontab()` - Execute `crontab -l`
- `load_system_crontabs()` - Parse system cron files
- `parse_cron_line()` - Convert cron line to CronJob object

### 3. Schedule Analyzer (`schedule_analyzer.py`)
**Purpose**: Determine which jobs match query criteria

**Core Logic**:
- Use `croniter` library for accurate schedule calculations
- Handle day-of-month OR day-of-week logic correctly
- Support date range queries and specific date matching

```python
class ScheduleAnalyzer:
    def matches_query(self, job: CronJob, criteria: QueryCriteria) -> bool
    def get_next_runs(self, job: CronJob, count: int = 10) -> List[datetime]
    def runs_on_date(self, job: CronJob, target_date: date) -> bool
    def runs_at_time(self, job: CronJob, hour: int, minute: int = None) -> bool
```

### 4. Output Formatter (`output_formatter.py`)
**Purpose**: Format results for human-readable display

**Output Formats**:
- List view (default)
- Table view (`--format table`)
- JSON output (`--format json`)

```
Jobs running on Saturday:
  - 0 2 * * 6    /path/to/backup.sh        (Every Saturday at 2:00 AM)
  - 0 8 1 * *    /path/to/monthly.sh       (1st of month at 8:00 AM - includes Saturdays when 1st falls on Saturday)
  - @weekly      /path/to/weekly-cleanup   (Every Sunday at midnight - does not match Saturday)
```

## Key Libraries

### croniter
```python
from croniter import croniter
from datetime import datetime

# Example usage
base = datetime.now()
iter = croniter('0 8 * * 1-5', base)  # 8 AM weekdays
next_run = iter.get_next(datetime)
```

**Why croniter?**
- Handles complex cron expressions correctly
- Supports the tricky day-of-month OR day-of-week logic
- Well-maintained and widely used
- Provides both forward and backward iteration

### argparse
```python
parser = argparse.ArgumentParser(description='Query crontab schedules')
parser.add_argument('query', help='Natural language query')
parser.add_argument('--format', choices=['list', 'table', 'json'], default='list')
parser.add_argument('--source', choices=['user', 'system', 'all'], default='user')
```

## File Structure
```
cron-query/
├── requirements.md
├── design.md
├── tasks.md                    # Implementation task breakdown
├── src/
│   ├── cron_query/
│   │   ├── __init__.py
│   │   ├── main.py            # CLI entry point
│   │   ├── query_parser.py    # Natural language parsing
│   │   ├── cron_loader.py     # Load crontab data
│   │   ├── schedule_analyzer.py # Match jobs to queries
│   │   └── output_formatter.py # Format results
│   └── tests/
│       ├── test_query_parser.py
│       ├── test_schedule_analyzer.py
│       └── fixtures/
│           └── sample_crontabs/
├── setup.py                   # Package setup
├── requirements.txt           # Python dependencies
└── README.md                  # Usage instructions
```

## Error Handling Strategy

### Graceful Degradation
- If user crontab fails to load, continue with system crontabs
- Invalid cron expressions should be reported but not crash the tool
- Permission errors should be clearly communicated

### Error Types
```python
class CronQueryError(Exception): pass
class InvalidCronExpression(CronQueryError): pass
class PermissionError(CronQueryError): pass
class InvalidQuery(CronQueryError): pass
```

## Testing Strategy

### Unit Tests
- Each component tested in isolation
- Mock crontab data for consistent testing
- Edge cases: invalid cron expressions, empty crontabs

### Integration Tests
- End-to-end query processing
- Real crontab data parsing
- Cross-platform compatibility (when testing on CentOS)

### Test Data
```
# Sample test crontab
0 2 * * 6     /backup.sh              # Saturday 2 AM
0 8 1 * *     /monthly.sh             # 1st of month 8 AM  
0 9 * * 1-5   /weekday-job.sh         # Weekdays 9 AM
*/15 * * * *  /frequent.sh            # Every 15 minutes
@daily        /daily-cleanup.sh       # Daily at midnight
```

## Future Considerations

### Performance Optimizations
- Cache parsed crontab data
- Lazy loading of system crontabs
- Parallel processing for large crontab sets

### Alternative Implementations (Learning Phase)
- **Go version**: Focus on performance and single-binary distribution
- **Rust version**: Explore memory safety and concurrent processing
- Compare approaches and document lessons learned

## Development Environment

### Local Development (Windows)
- Develop and test basic functionality
- Mock Linux cron behavior for initial development

### Testing Environment (CentOS via SSH)  
- Real crontab integration testing
- Cross-platform validation
- Performance testing with actual system loads

---

**Document Version**: 1.0  
**Last Updated**: 2025-09-12  
**Status**: Draft