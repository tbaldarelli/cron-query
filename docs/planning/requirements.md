# Crontab Query Tool - Requirements Specification

## Project Overview
A command-line tool for querying and analyzing crontab schedules using natural language queries. The tool addresses the complexity of understanding when cron jobs will execute, particularly handling the intricate logic of day-of-month vs day-of-week intersections.

## Target Environment
- **Platform**: Linux-based systems, Windows (development/testing with WSL or simulated crontab)
- **Terminal Support**: Unicode-aware terminals with automatic ASCII fallback
- **Cron Systems**: Vixie cron, cronie, systemd-cron
- **User Context**: System administrators, developers managing cron jobs

## Core Requirements

### 1. Query Interface
- **Natural Language Queries**: Support intuitive questions about job schedules
- **Command Line Interface**: Single command with query as argument
- **Output Format**: Clear, human-readable results

### 2. Supported Query Types

#### 2.1 Day-Based Queries
- "which jobs run on Saturday" (any Saturday)
- "which jobs run this Saturday" (specific date)
- "which jobs run on weekdays"
- "which jobs run on weekends"

#### 2.2 Time-Based Queries  
- "which jobs run at 8 AM"
- "which jobs run at 8:30 PM"
- "which jobs run between 9 AM and 5 PM"

#### 2.3 Date-Specific Queries
- "which jobs run on January 15th" (any year)
- "which jobs run on 2025-01-15" (specific date)
- "which jobs run on the 1st of every month"

#### 2.4 Future Extensibility (Phase 2)
- "which jobs run at 8 AM Monday through Friday"
- "which jobs run during business hours"
- "which jobs run on holidays"

### 3. Cron Job Sources
- User crontabs (`crontab -l`)
- System crontab (`/etc/crontab`)
- System cron directories (`/etc/cron.d/*`, `/etc/cron.daily/*`, etc.)
- Multiple user crontabs (with appropriate permissions)

### 4. Output and Display Requirements

#### 4.1 Unicode and Character Support
- **Unicode Characters**: Automatic detection of terminal Unicode support
- **ASCII Fallback**: Graceful degradation to ASCII characters when Unicode unavailable
- **Special Characters**: 
  - Bullet points: `•` (Unicode) / `*` (ASCII)
  - Error symbols: `❌` (Unicode) / `X` (ASCII)
  - Navigation arrows: `→←` (Unicode) / `->←` (ASCII)
- **Environment Controls**: Override detection with `CRON_QUERY_FORCE_ASCII` or `CRON_QUERY_FORCE_UNICODE`

#### 4.2 Template System
- **Predefined Templates**: compact, detailed, summary, verbose, csv_like
- **Custom Templates**: Support for user-defined output formatting
- **Special Character Variables**: `{bullet}`, `{error}`, `{next}`, `{prev}` template placeholders
- **Dynamic Character Selection**: Template variables automatically use appropriate character set

### 5. Technical Requirements

#### 5.1 Cron Expression Parsing
- **Standard Format**: `minute hour day-of-month month day-of-week command`
- **Special Characters**: `*`, `,`, `-`, `/`, `?`
- **Special Keywords**: `@yearly`, `@monthly`, `@weekly`, `@daily`, `@hourly`, `@reboot`
- **Complex Logic**: Proper handling of day-of-month OR day-of-week behavior

#### 5.2 Date/Time Calculations
- Current date/time awareness
- Future date projections
- Timezone handling (system timezone)
- Leap year considerations

#### 5.3 Error Handling
- Invalid cron expressions
- Permission denied errors
- Malformed queries
- Empty crontabs

## Non-Functional Requirements

### Performance
- Response time < 1 second for typical queries
- Handle crontabs with 100+ entries efficiently

### Usability
- Intuitive command syntax
- Helpful error messages with appropriate symbols
- Examples in help output
- Cross-terminal character compatibility
- Environment variable overrides for character display

### Reliability
- Accurate cron expression interpretation
- Consistent results across different Linux distributions

## Technical Constraints

### Dependencies
- Must work on standard Linux installations
- Python 3.6+ with pip
- Key Python libraries: `croniter`, `argparse`, `datetime`
- No root privileges required for user crontabs

### Compatibility
- POSIX-compliant systems
- Common cron implementations
- Primary implementation: Python
- Future learning implementations: Go, Rust

## Success Criteria

### MVP (Minimum Viable Product)
1. Parse user crontab successfully
2. Answer basic day and time queries accurately
3. Handle the day-of-month/day-of-week OR logic correctly
4. Provide clear, formatted output

### Future Enhancements
1. Natural language processing improvements
2. Multiple crontab sources
3. Job scheduling predictions
4. Integration with system monitoring tools

## Example Usage

```bash
# Install/setup
cron-query --help

# Basic queries
cron-query "which jobs run on Saturday"
cron-query "which jobs run at 8 AM"
cron-query "which jobs run this Saturday"

# Expected output format
Jobs running on Saturday:
  - 0 2 * * 6    /path/to/backup.sh     (Every Saturday at 2:00 AM)
  - 0 8 1 * *    /path/to/monthly.sh    (1st of month at 8:00 AM - includes Saturdays when 1st falls on Saturday)
```

## Assumptions
1. Users have basic understanding of cron job concepts
2. System has standard cron implementation
3. Crontab files are readable by the user
4. System date/time is correctly configured

## Out of Scope (Initial Version)
- Crontab editing/modification
- Job execution monitoring
- Remote system crontab analysis
- GUI interface
- Email notification analysis

---

**Document Version**: 1.1  
**Last Updated**: 2025-09-24  
**Status**: Updated - Unicode Support Added
