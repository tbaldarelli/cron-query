# Technology Stack

## Dual Implementation

This project maintains both Python and Groovy implementations:

### Python Implementation
- **Language**: Python 3.7+
- **Package Manager**: pip
- **Build Tool**: setuptools
- **Testing**: pytest with coverage
- **Dependencies**:
  - `croniter` - cron expression parsing
  - `python-dateutil` - date/time utilities
  - `colorama` - cross-platform colored terminal output
  - `tabulate` - table formatting
  - `PyYAML` - YAML output support

### Groovy Implementation
- **Language**: Groovy 4.0.15 on Java 21
- **Build Tool**: Gradle 8.11.1
- **Testing**: Spock Framework 2.3
- **Dependencies**:
  - `cron-utils` - cron parsing library
  - `picocli` - CLI argument parsing
  - `joda-time` - date/time utilities
  - `jansi` - ANSI colors for terminal output
  - `snakeyaml` - YAML support

## Common Build Commands

### Python
```bash
# Install in development mode
pip install -e .

# Run tests
pytest

# Run tests with coverage
pytest --cov=cron_query

# Build package
python setup.py sdist bdist_wheel
```

### Groovy/Java
```bash
# Build project
./gradlew build

# Run tests
./gradlew test

# Create executable JAR
./gradlew jar

# Run application
./gradlew run --args="jobs on Saturday"
```

## Core Architecture Components

### Python Implementation Components
- **Query Parser** (`query_parser.py`): Natural language → structured criteria
- **Cron Loader** (`cron_loader.py`): Load/parse crontab from multiple sources  
- **Schedule Analyzer** (`schedule_analyzer.py`): Match jobs to query criteria using croniter
- **Output Formatter** (`output_formatter.py`): Format results with template system
- **Special Characters** (`special_chars.py`): Unicode/ASCII character management

### Key Technical Decisions
- **croniter Library**: Handles complex day-of-month OR day-of-week logic correctly
- **Template System**: Predefined and custom templates with special character variables
- **Unicode Detection**: Automatic terminal capability detection with environment overrides

## Cross-Platform Considerations

- **Line Endings**: All files use LF (`\n`) - handled by `.gitattributes`
- **File Paths**: Always use `os.path.join()` in Python, proper path handling in Groovy
- **Permissions**: Scripts set to executable (755) during packaging
- **Development Workflow**: Windows development with mock data, Linux deployment testing
- **Character Encoding**: Robust Unicode/ASCII fallback for terminal compatibility