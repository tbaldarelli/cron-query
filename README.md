# cron-query

A natural language interface for querying and analyzing cron schedules.

## Features

- Natural language queries: "Which jobs run on weekends?" or "What runs at 8 AM?"
- Support for relative dates: "this Saturday", "next Monday"
- Time range queries: "between 9 AM and 5 PM", "after 10 PM"
- System crontab support: Query jobs from user crontab, system crontab, or both
- Multiple output formats: list, table, JSON, CSV, YAML
- Color-coded terminal output
- Custom output templates
- Cross-platform: Works on Linux and Windows (with mock data)

## Installation

```bash
# From PyPI (recommended)
pip install cron-query

# From source
git clone https://github.com/yourusername/cron-query.git
cd cron-query
pip install -e .
```

## Quick Start

```bash
# Basic queries
cron-query "jobs on Saturday"
cron-query "jobs at 8 AM"
cron-query "jobs on weekdays"

# Relative dates
cron-query "jobs this Saturday"
cron-query "jobs next Monday"
cron-query "jobs coming weekend"

# Time ranges
cron-query "jobs after 10 AM"
cron-query "jobs before 5 PM"
cron-query "jobs between 9 AM and 5 PM"

# Combined queries
cron-query "jobs on weekdays between 9 AM and 5 PM"
cron-query "jobs this Saturday after 10 AM"
```

## Output Formats

```bash
# Default list format
cron-query "jobs on Saturday"

# Table format
cron-query --format table "jobs on Saturday"

# JSON format (useful for scripting)
cron-query --format json "jobs on Saturday"

# CSV format (spreadsheet-friendly)
cron-query --format csv "jobs on Saturday"

# YAML format
cron-query --format yaml "jobs on Saturday"
```

## Templates

```bash
# List available templates
cron-query --list-templates

# Use a predefined template
cron-query --template compact "jobs on Saturday"

# Custom template
cron-query --template "Job {index}: {command} ({expression})" "jobs on Saturday"

# Get template help
cron-query --template-help
```

## Sources

```bash
# Query user's crontab (default)
cron-query "jobs on weekdays"

# Query system crontabs (/etc/crontab and /etc/cron.d/*)
cron-query --source system "jobs on weekdays"

# Query both user and system crontabs
cron-query --source all "jobs on weekdays"

# Query a specific crontab file
cron-query --file /path/to/crontab "jobs on weekdays"
```

## Options

```bash
# Show verbose output
cron-query -v "jobs on weekdays"

# Disable colors
cron-query --no-color "jobs on weekdays"

# Control pagination
cron-query --page-size 10 --page 2 "jobs on weekdays"
cron-query --no-pager "jobs on weekdays"
```

## Requirements

- Python 3.7 or higher
- `croniter` package (installed automatically)
- Optional: `pyyaml` package for YAML output format

## Contributing

Pull requests are welcome! Please make sure tests pass and add tests for new features.

## License

MIT License - see LICENSE file for details