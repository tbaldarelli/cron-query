# Product Overview

cron-query is a natural language interface for querying and analyzing cron schedules. It addresses the complexity of understanding when cron jobs will execute, particularly handling the intricate logic of day-of-month vs day-of-week intersections.

## Core Problem Solved

Traditional cron schedule analysis requires manual parsing of complex expressions and understanding the subtle OR logic between day-of-month and day-of-week fields. This tool translates natural language queries into accurate schedule analysis.

## Key Features

- **Natural Language Queries**: "Which jobs run on weekends?", "What runs at 8 AM?", "Jobs this Saturday after 10 AM"
- **Advanced Query Support**: Relative dates, time ranges, combined day/time queries
- **Multiple Data Sources**: User crontab, system crontab (/etc/crontab), cron directories (/etc/cron.d/*)
- **Rich Output Formats**: List, table, JSON, CSV, YAML with customizable templates
- **Unicode/ASCII Support**: Automatic terminal detection with environment overrides
- **Cross-Platform**: Linux (real crontab access), Windows (development with mock data)

## Target Users

- **System Administrators**: Managing complex cron job schedules across multiple systems
- **Developers**: Understanding existing scheduled tasks and their execution patterns
- **DevOps Engineers**: Analyzing job scheduling conflicts and system load patterns

## Architecture Philosophy

**Component-Based Design**: Clear separation between query parsing, cron loading, schedule analysis, and output formatting for maintainability and testability.

**Dual Implementation Strategy**: 
- **Python**: Primary implementation for broad compatibility and rapid development
- **Groovy**: Alternative JVM-based implementation for enterprise environments

**Spec-Driven Development**: Requirements → Design → Implementation with comprehensive task breakdown and milestone tracking.