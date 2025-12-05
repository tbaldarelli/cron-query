# Groovy JAR Integration Documentation

## Investigation Summary

### JAR Structure
The `cron-query-groovy-1.2.2.jar` contains the following main classes in the `com.cronquery` package:

#### Core Classes
- **Main**: CLI entry point with picocli command-line parsing
- **CronLoader**: Loads and parses crontab data from various sources
- **QueryParser**: Parses natural language queries into structured criteria
- **ScheduleAnalyzer**: Matches cron jobs against query criteria
- **OutputFormatter**: Formats results in various output formats

#### Model Classes
- **CronJob**: Represents a single cron job with schedule and command
- **QueryCriteria**: Structured query criteria with day/time filters
- **QueryType**: Enum for query types (DAY_BASED, TIME_BASED, COMBINED, UNKNOWN)
- **OutputFormat**: Enum for output formats

#### Exception Classes
- **CronParseException**: Thrown when parsing cron lines fails
- **QueryParseException**: Thrown when parsing queries fails
- **ScheduleAnalysisException**: Thrown when schedule analysis fails
- **OutputFormatterException**: Thrown when output formatting fails

### Public API Methods

#### CronLoader
```groovy
static List<CronJob> loadUserCrontab(String user = null)
static List<CronJob> loadCrontabFromFile(String filePath)
static CronJob parseCronLine(String line, String source = 'user', String user = null)
```

#### QueryParser
```groovy
static QueryCriteria parseQuery(String query)
static String formatCriteriaDescription(QueryCriteria criteria)
```

#### ScheduleAnalyzer
```groovy
static List<CronJob> findMatchingJobs(List<CronJob> cronJobs, QueryCriteria criteria)
static boolean matchesCriteria(CronJob job, QueryCriteria criteria)
static List<ZonedDateTime> getNextRuns(CronJob job, int count = 5, ZonedDateTime startTime = null)
```

### Integration Approach

**Direct Class Invocation** (Recommended)
- The Groovy JAR exposes a clean public API through static methods
- No need for process execution or CLI argument parsing
- Type-safe integration with proper exception handling
- Better performance and easier testing

### Integration Flow

1. **Load Crontab Data**: Use `CronLoader.loadCrontabFromFile()` with test file or system crontab
2. **Parse Query**: Use `QueryParser.parseQuery()` to convert natural language to criteria
3. **Find Matches**: Use `ScheduleAnalyzer.findMatchingJobs()` to filter jobs
4. **Transform Results**: Convert Groovy `CronJob` objects to Spring Boot API models

### Key Considerations

1. **Groovy-Java Interop**: Groovy classes are fully compatible with Java
2. **Exception Handling**: Catch Groovy exceptions and translate to Spring exceptions
3. **Model Conversion**: Map between Groovy models and Spring Boot DTOs
4. **Immutability**: Groovy models use `@Immutable` annotation
5. **Static Methods**: All core methods are static, no instance management needed

### Dependencies

The Groovy JAR includes:
- cron-utils: For cron expression parsing and execution time calculation
- picocli: For CLI (not needed for library usage)
- jansi: For colored output (not needed for library usage)

## Implementation Plan

1. Create `GroovyJarAdapter` interface defining the integration contract
2. Implement `GroovyJarAdapterImpl` with direct method invocations
3. Create model converters between Groovy and Spring Boot models
4. Implement exception translation layer
5. Add logging for debugging and monitoring
