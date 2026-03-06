# Design Document: Java Native Implementation

## Overview

This design replaces the Groovy JAR dependency in the Spring Boot microservice with pure Java 21 components. The current architecture uses a GroovyJarAdapter to wrap Groovy implementations of cron parsing and query processing. This replacement eliminates the Groovy dependency while maintaining full API compatibility and achieving feature parity with the Python implementation.

The design follows a component-based architecture with clear separation of concerns:
- **CronParser**: Validates and parses cron expressions using the cron-utils library
- **QueryParser**: Translates natural language queries into structured criteria
- **ScheduleAnalyzer**: Matches cron jobs against query criteria and calculates next run times
- **CronJobService**: Orchestrates the components and integrates with Spring Boot

The existing Spring Boot infrastructure (controllers, models, configuration, health checks, metrics) remains unchanged. Only the Groovy JAR adapter and its implementation are replaced with Java components.

### Key Design Goals

1. **Zero Breaking Changes**: Maintain 100% API compatibility with existing REST endpoints
2. **Feature Parity**: Support all query patterns from the Python implementation
3. **Modern Java**: Leverage Java 21 features (records, pattern matching, text blocks, java.time)
4. **Testability**: Design components for isolated unit testing with clear interfaces
5. **Performance**: Process queries in under 500ms for crontabs with up to 1000 jobs


## Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot Layer                         │
│  ┌──────────────────┐         ┌──────────────────┐         │
│  │ CronQueryController│────────│ GlobalException  │         │
│  │                  │         │    Handler       │         │
│  └────────┬─────────┘         └──────────────────┘         │
│           │                                                  │
│           ▼                                                  │
│  ┌──────────────────────────────────────────────┐          │
│  │        CronQueryServiceImpl                   │          │
│  │  (Orchestration & Metrics)                   │          │
│  └───┬──────────────────────────────────────┬───┘          │
│      │                                      │               │
└──────┼──────────────────────────────────────┼───────────────┘
       │                                      │
       ▼                                      ▼
┌──────────────────┐              ┌──────────────────┐
│  CrontabLoader   │              │  CronJobService  │
│  (Existing)      │              │  (New)           │
└──────────────────┘              └────────┬─────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
                    ▼                      ▼                      ▼
          ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
          │   QueryParser    │  │   CronParser     │  │ ScheduleAnalyzer │
          │   (New)          │  │   (New)          │  │   (New)          │
          └──────────────────┘  └──────────────────┘  └──────────────────┘
                    │                      │                      │
                    └──────────────────────┴──────────────────────┘
                                           │
                                           ▼
                                  ┌──────────────────┐
                                  │   cron-utils     │
                                  │   (Library)      │
                                  └──────────────────┘
```

### Data Flow

1. **Query Request**: Controller receives QueryRequest (natural language or structured)
2. **Service Orchestration**: CronQueryServiceImpl delegates to CronJobService
3. **Crontab Loading**: CrontabLoader provides raw crontab content (unchanged)
4. **Query Parsing**: QueryParser converts natural language to QueryCriteria
5. **Cron Parsing**: CronParser validates and parses each cron expression
6. **Schedule Analysis**: ScheduleAnalyzer matches jobs against criteria
7. **Next Run Calculation**: ScheduleAnalyzer calculates next execution times
8. **Response Building**: Service assembles QueryResponse with results
9. **Response Formatting**: Controller formats response (JSON/CSV/YAML)


## Components and Interfaces

### 1. CronParser

**Responsibility**: Parse and validate cron expressions using cron-utils library.

**Interface**:
```java
// Note: Simplified interface showing method signatures.
// Actual implementation should include proper Javadoc, logging, and error handling.

public interface CronParser {
    /**
     * Parse a cron expression into a structured representation.
     * @param cronExpression 5-field cron expression (minute hour dom month dow)
     * @return Parsed cron object from cron-utils
     * @throws CronParseException if expression is invalid
     */
    Cron parse(String cronExpression) throws CronParseException;
    
    /**
     * Validate a cron expression without parsing.
     * @param cronExpression Expression to validate
     * @return true if valid, false otherwise
     */
    boolean validate(String cronExpression);
    
    /**
     * Format a parsed cron expression back to string.
     * @param cron Parsed cron object
     * @return Formatted cron expression string
     */
    String format(Cron cron);
}
```

**Implementation Details**:
- Use `CronDefinition` from cron-utils with Unix cron format (5 fields)
- Configure cron-utils parser for standard cron syntax
- Handle special characters: `*`, `,`, `-`, `/`, `?`
- Validate field ranges: minute (0-59), hour (0-23), dom (1-31), month (1-12), dow (0-6)
- Provide descriptive error messages for invalid expressions
- Support both 0 and 7 for Sunday in day-of-week field

**Dependencies**:
- `com.cronutils:cron-utils:9.2.0` (or latest stable)


### 2. QueryParser

**Responsibility**: Parse natural language queries into structured criteria.

**Interface**:
```java
// Note: Simplified interface showing method signatures.
// Actual implementation should include proper Javadoc, logging, and error handling.

public interface QueryParser {
    /**
     * Parse a natural language query into structured criteria.
     * @param query Natural language query string
     * @return Parsed query criteria
     * @throws InvalidQueryException if query cannot be parsed
     */
    QueryCriteria parse(String query) throws InvalidQueryException;
    
    /**
     * Normalize a query by removing common prefixes.
     * @param query Raw query string
     * @return Normalized query string
     */
    String normalize(String query);
}
```

**Data Model** (using Java records):
```java
public record QueryCriteria(
    QueryType type,
    Set<DayOfWeek> daysOfWeek,
    LocalTime exactTime,
    TimeRange timeRange,
    LocalDate specificDate,
    boolean isRelativeDate,
    String rawQuery
) {
    public enum QueryType {
        DAY_BASED, TIME_BASED, COMBINED, UNKNOWN
    }
    
    public record TimeRange(
        LocalTime start,
        LocalTime end,
        RangeType type  // AFTER, BEFORE, BETWEEN
    ) {}
}
```

**Parsing Patterns** (from Python implementation):

1. **Day Patterns**:
   - Day names: "Monday", "Saturday", "weekends", "weekdays"
   - Relative dates: "today", "tomorrow", "yesterday"
   - Specific dates: "this Saturday", "next Monday", "coming Friday"
   - Explicit dates: "9/18/2025", "2025-09-18"
   - Combined: "Saturday 9/18/2025" (with conflict detection)

2. **Time Patterns**:
   - 12-hour format: "8 AM", "8:30 PM", "noon", "midnight"
   - 24-hour format: "08:00", "20:30"
   - Time ranges: "after 10 AM", "before 5 PM", "between 9 AM and 5 PM"

3. **Combined Patterns**:
   - "this Saturday after 10 AM"
   - "weekdays between 9 AM and 5 PM"
   - "Saturday 9/18/2025 between 8 PM and 11 PM"

**Implementation Strategy**:
- Use regex patterns for each query type (similar to Python)
- Try combined patterns first, then day-only, then time-only
- Normalize queries by removing prefixes: "which jobs run", "show me jobs that run", "find jobs"
- Handle common typos: "comming" → "coming"
- Validate date conflicts: "Saturday 9/18/2025" where 9/18/2025 is not a Saturday
- Calculate relative dates using `java.time` APIs


### 3. ScheduleAnalyzer

**Responsibility**: Match cron jobs against query criteria and calculate next run times.

**Interface**:
```java
// Note: Simplified interface showing method signatures.
// Actual implementation should include proper Javadoc, logging, and error handling.

public interface ScheduleAnalyzer {
    /**
     * Find cron jobs matching the given criteria.
     * @param jobs List of parsed cron jobs
     * @param criteria Query criteria to match
     * @return List of matching jobs
     */
    List<CronJob> findMatching(List<CronJob> jobs, QueryCriteria criteria);
    
    /**
     * Calculate next N execution times for a cron job.
     * @param cronExpression Cron expression
     * @param count Number of executions to calculate
     * @param from Starting time (null for now)
     * @return List of next execution times
     * @throws ScheduleAnalysisException if calculation fails
     */
    List<ZonedDateTime> calculateNextRuns(String cronExpression, int count, ZonedDateTime from)
        throws ScheduleAnalysisException;
    
    /**
     * Check if a cron expression matches query criteria.
     * @param cronExpression Cron expression to check
     * @param criteria Query criteria
     * @return true if matches, false otherwise
     */
    boolean matches(String cronExpression, QueryCriteria criteria);
}
```

**Matching Logic**:

1. **Day-of-Week Matching**:
   - Handle cron's OR logic: when both DOM and DOW are specified, job runs when EITHER matches
   - Use cron-utils `ExecutionTime` to check if job runs on target days
   - Sample future executions to determine day-of-week patterns

2. **Time Matching**:
   - Parse hour and minute fields from cron expression
   - For exact time: check if hour and minute match
   - For time ranges: check if any execution falls within range
   - Handle overnight ranges: "10 PM to 6 AM"

3. **Combined Matching**:
   - Both day and time criteria must match (AND logic)
   - For specific dates: check if job runs on that exact date
   - For relative dates: calculate target date and check execution

4. **Next Run Calculation**:
   - Use cron-utils `ExecutionTime.forCron()`
   - Call `nextExecution()` repeatedly to get N future runs
   - Format as "yyyy-MM-dd HH:mm:ss z" using system default timezone
   - Handle edge cases: expressions that never execute again

**Implementation Notes**:
- Use `ZonedDateTime` for all time calculations
- Default to system timezone for consistency with Python
- Gracefully skip invalid cron expressions (log warning, continue processing)
- Cache parsed cron objects for performance


### 4. CronJobService

**Responsibility**: Orchestrate query processing workflow and coordinate all components.

**Interface**:
```java
// Note: Simplified interface showing method signatures.
// Actual implementation should include proper Javadoc, logging, and error handling.

public interface CronJobService {
    /**
     * Execute a query against crontab content.
     * @param request Query request (natural language or structured)
     * @param crontabContent Raw crontab content
     * @return List of matching cron jobs with next run times
     * @throws InvalidQueryException if query is invalid
     * @throws CronParseException if cron parsing fails
     */
    List<CronJob> executeQuery(QueryRequest request, String crontabContent)
        throws InvalidQueryException, CronParseException;
    
    /**
     * Load all cron jobs from crontab content.
     * @param crontabContent Raw crontab content
     * @return List of all parsed cron jobs
     */
    List<CronJob> loadAllJobs(String crontabContent);
    
    /**
     * Validate a cron expression.
     * @param cronExpression Expression to validate
     * @return true if valid, false otherwise
     */
    boolean validateCronExpression(String cronExpression);
}
```

**Workflow**:

1. **Query Processing**:
   - If natural language query: use QueryParser to get QueryCriteria
   - If structured query: build QueryCriteria from parameters
   - Parse crontab content into individual job lines
   - For each job: parse cron expression using CronParser
   - Use ScheduleAnalyzer to find matching jobs
   - Calculate next 3 run times for each matching job
   - Build CronJob model objects with all details

2. **Error Handling**:
   - Invalid queries: throw InvalidQueryException with descriptive message
   - Invalid cron expressions: log warning, skip job, continue processing
   - If all jobs fail to parse: throw CrontabLoadException
   - Wrap unexpected errors in appropriate service exceptions

3. **Crontab Parsing**:
   - Split content by newlines
   - Skip empty lines and comments (lines starting with #)
   - Parse each line: extract cron expression, command, user (if present)
   - Handle both user crontab format (5 fields + command) and system format (6 fields with user)
   - Track source information for each job

**Integration with CronQueryServiceImpl**:
- CronQueryServiceImpl will inject CronJobService instead of GroovyJarAdapter
- Update executeQuery() to call cronJobService.executeQuery()
- Update getAllJobs() to call cronJobService.loadAllJobs()
- Update checkHealth() to call cronJobService.validateCronExpression()
- Maintain all existing metrics tracking (invocation counter, execution timer)


### 5. Exception Hierarchy

**Design**: Create a clear exception hierarchy for different failure modes.

**Note**: The following shows the exception class structure. Actual implementation should follow the existing exception pattern in the codebase (constructor chaining, proper serialization, etc.).

```java
// Base exception
public class CronQueryException extends RuntimeException {
    public CronQueryException(String message) { super(message); }
    public CronQueryException(String message, Throwable cause) { super(message, cause); }
}

// Cron parsing errors
public class CronParseException extends CronQueryException {
    private final String invalidExpression;
    
    public CronParseException(String message, String invalidExpression) {
        super(message);
        this.invalidExpression = invalidExpression;
    }
    
    public String getInvalidExpression() { return invalidExpression; }
}

// Query parsing errors
public class InvalidQueryException extends CronQueryException {
    private final String invalidQuery;
    
    public InvalidQueryException(String message, String invalidQuery) {
        super(message);
        this.invalidQuery = invalidQuery;
    }
    
    public String getInvalidQuery() { return invalidQuery; }
}

// Schedule analysis errors
public class ScheduleAnalysisException extends CronQueryException {
    public ScheduleAnalysisException(String message) { super(message); }
    public ScheduleAnalysisException(String message, Throwable cause) { super(message, cause); }
}

// Crontab loading errors (already exists)
public class CrontabLoadException extends CronQueryException {
    public CrontabLoadException(String message) { super(message); }
    public CrontabLoadException(String message, Throwable cause) { super(message, cause); }
}
```

**Exception Handling Strategy**:
- CronParseException: Include invalid expression in exception for debugging
- InvalidQueryException: Include problematic query text for user feedback
- ScheduleAnalysisException: Wrap underlying errors with context
- GlobalExceptionHandler: Map exceptions to appropriate HTTP status codes
  - InvalidQueryException → 400 Bad Request
  - CrontabLoadException → 500 Internal Server Error
  - CronParseException → 500 Internal Server Error (if all jobs fail)
  - ScheduleAnalysisException → 500 Internal Server Error


## Data Models

### Existing Models (Unchanged)

The following models remain unchanged and continue to be used:

**QueryRequest**: Request model for API endpoints
- Fields: query, day, time, timeRange, format
- Methods: isNaturalLanguageQuery(), isStructuredQuery()

**QueryResponse**: Response model for API endpoints
- Fields: jobs, totalCount, query, sources, executionTimeMs
- Used by controller to return results

**CronJob**: Model representing a cron job
- Fields: schedule, command, source, user, nextRuns, description
- Used throughout the system to represent parsed jobs

**ErrorResponse**: Error response model
- Fields: status, error, message, path, timestamp
- Used by GlobalExceptionHandler

**HealthStatus**: Health check response model
- Fields: status, details, availableSources
- Used by health check endpoint

### New Internal Models

**QueryCriteria** (Java record):
```java
public record QueryCriteria(
    QueryType type,
    Set<DayOfWeek> daysOfWeek,
    LocalTime exactTime,
    TimeRange timeRange,
    LocalDate specificDate,
    boolean isRelativeDate,
    String rawQuery
) {
    public enum QueryType {
        DAY_BASED,      // "jobs on Saturday"
        TIME_BASED,     // "jobs at 8 AM"
        COMBINED,       // "jobs on Saturday at 8 AM"
        UNKNOWN         // Could not parse
    }
    
    public record TimeRange(
        LocalTime start,
        LocalTime end,
        RangeType type
    ) {
        public enum RangeType {
            AFTER,      // "after 10 AM"
            BEFORE,     // "before 5 PM"
            BETWEEN     // "between 9 AM and 5 PM"
        }
    }
    
    // Helper methods
    public boolean hasDayCriteria() {
        return daysOfWeek != null && !daysOfWeek.isEmpty();
    }
    
    public boolean hasTimeCriteria() {
        return exactTime != null || timeRange != null;
    }
    
    public boolean isSpecificDate() {
        return specificDate != null;
    }
}
```

**ParsedCronLine** (internal representation):
```java
public record ParsedCronLine(
    String cronExpression,
    String command,
    String user,
    String source,
    int lineNumber,
    boolean isValid
) {
    // Factory methods for different crontab formats
    public static ParsedCronLine fromUserCrontab(String line, String source, int lineNumber) {
        // Parse: minute hour dom month dow command
    }
    
    public static ParsedCronLine fromSystemCrontab(String line, String source, int lineNumber) {
        // Parse: minute hour dom month dow user command
    }
}
```

### Model Conversion

**QueryRequest → QueryCriteria**:
- Natural language: QueryParser.parse(request.getQuery())
- Structured: Build QueryCriteria from day/time/timeRange fields

**ParsedCronLine → CronJob**:
- Calculate next runs using ScheduleAnalyzer
- Generate human-readable description
- Populate all CronJob fields


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property Reflection

After analyzing all acceptance criteria, I identified several opportunities to consolidate redundant properties:

1. **Query Parser Properties**: Multiple criteria (10.1-10.7) all test that QueryParser supports various patterns. These can be consolidated into comprehensive properties that cover all pattern types.

2. **Schedule Analyzer Matching**: Criteria 4.1, 4.2, and 4.3 test day matching, time matching, and combined matching separately. These represent the same underlying matching logic with different inputs.

3. **API Response Properties**: Criteria 6.4, 6.5, 6.6 all test HTTP status codes for different scenarios. These can be combined into properties about correct status code mapping.

4. **Error Handling**: Criteria 8.1, 8.2 test exception throwing for different parsers. These follow the same pattern and can be unified.

The following properties eliminate redundancy while ensuring comprehensive coverage:

### Property 1: Cron Expression Round-Trip

*For any* valid cron expression, parsing then formatting then parsing again SHALL produce an equivalent cron expression.

**Validates: Requirements 2.7**

**Rationale**: This is a classic round-trip property that validates both parsing and formatting are inverse operations. If we can parse an expression, format it, and parse it again to get the same result, we know both operations are correct.

### Property 2: Cron Validation Consistency

*For any* cron expression string, if parse() succeeds then validate() SHALL return true, and if parse() throws CronParseException then validate() SHALL return false.

**Validates: Requirements 2.2, 2.3**

**Rationale**: Validation and parsing must be consistent. An expression cannot be both valid and unparseable, or invalid and parseable.


### Property 3: Query Parser Completeness

*For any* natural language query containing recognizable day or time patterns, QueryParser SHALL extract the corresponding criteria and NOT return QueryType.UNKNOWN.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6**

**Rationale**: This property consolidates all the query parsing requirements. If a query contains patterns we claim to support (days, times, ranges, combinations), we must successfully parse it. This is more comprehensive than testing each pattern type separately.

### Property 4: Query Normalization Idempotence

*For any* query string, normalizing it twice SHALL produce the same result as normalizing it once: normalize(normalize(q)) = normalize(q).

**Validates: Requirements 13.8**

**Rationale**: Normalization should be idempotent. Applying it multiple times should not change the result after the first application.

### Property 5: Time Format Normalization

*For any* valid time specification (12-hour with AM/PM, 24-hour, or special names like "noon"), QueryParser SHALL normalize it to a consistent LocalTime representation.

**Validates: Requirements 10.7**

**Rationale**: Different time formats should normalize to the same internal representation. "8 PM", "20:00", and "8:00 PM" should all produce LocalTime.of(20, 0).

### Property 6: Date Validation

*For any* explicit date string, if it represents an invalid calendar date (e.g., "2/30/2025"), QueryParser SHALL throw InvalidQueryException.

**Validates: Requirements 13.10**

**Rationale**: The parser must reject impossible dates. This prevents downstream errors from invalid date calculations.

### Property 7: Day-Date Conflict Detection

*For any* query combining a day name and explicit date where the date does not fall on that day, QueryParser SHALL throw InvalidQueryException with a descriptive conflict message.

**Validates: Requirements 13.3**

**Rationale**: "Saturday 9/18/2025" where 9/18/2025 is a Thursday should be rejected with a clear explanation of the conflict.


### Property 8: Schedule Matching Correctness

*For any* cron job and query criteria, if the job matches the criteria, then at least one of the job's next 100 executions SHALL satisfy the criteria's day and time constraints.

**Validates: Requirements 4.1, 4.2, 4.3**

**Rationale**: This consolidates day matching, time matching, and combined matching into one property. If we claim a job matches, we must be able to demonstrate it by showing actual execution times that satisfy the criteria. Checking 100 executions provides sufficient coverage for most cron patterns.

### Property 9: Next Run Count

*For any* valid cron expression and positive integer N, calculateNextRuns(expression, N, startTime) SHALL return exactly N execution times, each later than startTime and in chronological order.

**Validates: Requirements 4.5, 11.1**

**Rationale**: When we ask for N next runs, we should get exactly N results, all in the future, and properly ordered.

### Property 10: Next Run Format

*For any* execution time returned by calculateNextRuns(), formatting it SHALL produce a string matching the pattern "yyyy-MM-dd HH:mm:ss z".

**Validates: Requirements 11.3**

**Rationale**: All execution times must be formatted consistently according to the specified pattern.

### Property 11: Graceful Degradation

*For any* list of cron jobs where at least one has a valid expression and at least one has an invalid expression, findMatching() SHALL return results from the valid jobs and NOT throw an exception.

**Validates: Requirements 4.6, 8.4**

**Rationale**: Invalid jobs should not prevent processing of valid jobs. The system should log warnings and continue.

### Property 12: Query Response Completeness

*For any* successful query execution, the QueryResponse SHALL contain non-null values for jobs, totalCount, query, sources, and executionTimeMs fields.

**Validates: Requirements 5.7**

**Rationale**: Every successful response must include all required fields. Missing fields indicate incomplete processing.


### Property 13: HTTP Status Code Mapping

*For any* API request, the response status code SHALL be 200 for successful queries, 400 for InvalidQueryException, and 500 for CrontabLoadException or ScheduleAnalysisException.

**Validates: Requirements 6.4, 6.5, 6.6**

**Rationale**: Exception types must map consistently to HTTP status codes. This ensures proper REST API semantics.

### Property 14: API Compatibility

*For any* QueryRequest, the structure of the returned QueryResponse JSON SHALL match the existing Groovy implementation's response structure (same field names, types, and nesting).

**Validates: Requirements 6.3**

**Rationale**: API compatibility requires identical JSON structure. Clients should not be able to distinguish between old and new implementations based on response format.

### Property 15: Exception Context Preservation

*For any* parsing error (cron or query), the thrown exception SHALL include the invalid input string in its message or as a field.

**Validates: Requirements 8.1, 8.2**

**Rationale**: Error messages must include the problematic input to aid debugging. Users need to know what input caused the failure.

### Property 16: Empty Result Handling

*For any* query criteria that matches zero jobs, the QueryResponse SHALL have an empty jobs list, totalCount of 0, and still include valid query, sources, and executionTimeMs fields.

**Validates: Requirements 5.7**

**Rationale**: Empty results are valid and should return a complete response structure, not an error or null.


## Error Handling

### Error Handling Strategy

The implementation follows a **graceful degradation** approach where individual failures do not prevent overall processing:

1. **Single Job Failures**: If one cron job has an invalid expression, log a warning and continue processing other jobs
2. **Partial Results**: Return whatever valid results can be obtained rather than failing completely
3. **Clear Error Messages**: All exceptions include context about what failed and why
4. **Structured Errors**: Use specific exception types for different failure modes

### Error Scenarios and Responses

| Scenario | Exception | HTTP Status | Response |
|----------|-----------|-------------|----------|
| Invalid query syntax | InvalidQueryException | 400 | ErrorResponse with query text |
| Unparseable cron expression (all jobs) | CrontabLoadException | 500 | ErrorResponse with context |
| Unparseable cron expression (single job) | Logged warning | 200 | Continue processing, exclude job |
| No crontab sources available | CrontabLoadException | 500 | ErrorResponse |
| Schedule calculation fails | ScheduleAnalysisException | 500 | ErrorResponse with job details |
| Invalid date in query | InvalidQueryException | 400 | ErrorResponse with date |
| Day-date conflict in query | InvalidQueryException | 400 | ErrorResponse with conflict details |
| Empty query | InvalidQueryException | 400 | ErrorResponse |
| No matching jobs | None | 200 | QueryResponse with empty jobs list |

### Logging Strategy

**Error Level**: Use for failures that prevent request completion
- CrontabLoadException (no sources available)
- ScheduleAnalysisException (unexpected failures)
- Uncaught exceptions

**Warn Level**: Use for recoverable issues
- Invalid cron expression in single job
- Cron expression that will never execute again
- Query patterns that are ambiguous but parseable

**Info Level**: Use for normal operations
- Successful query execution
- Number of jobs matched
- Execution time metrics

**Debug Level**: Use for detailed tracing
- Query parsing steps
- Cron expression parsing details
- Schedule matching logic

### Exception Translation

GlobalExceptionHandler translates internal exceptions to HTTP responses:

**Note**: The following examples show the exception handling pattern. The existing GlobalExceptionHandler already has proper logging, metrics tracking, and HttpServletRequest injection. Preserve the existing structure and just add/remove handlers as needed.

```java
// ADD: New handler for ScheduleAnalysisException
// Pattern: Copy from existing handleGroovyJarException, rename exception type
@ExceptionHandler(ScheduleAnalysisException.class)
public ResponseEntity<ErrorResponse> handleScheduleAnalysis(ScheduleAnalysisException ex, HttpServletRequest request) {
    // Keep existing pattern: logging, metrics, ErrorResponse construction
}

// REMOVE: handleGroovyJarException (exception type going away)

// KEEP: All other handlers unchanged (handleInvalidQueryException, handleCrontabLoadException, etc.)
```


## Testing Strategy

### Dual Testing Approach

The implementation requires both unit tests and property-based tests for comprehensive coverage:

**Unit Tests**: Verify specific examples, edge cases, and error conditions
- Specific query patterns: "jobs on Saturday", "jobs at 8 AM"
- Edge cases: empty crontab, malformed entries, timezone boundaries
- Error conditions: invalid queries, unparseable cron expressions
- Integration points: controller → service → components

**Property-Based Tests**: Verify universal properties across all inputs
- Generate random valid/invalid cron expressions
- Generate random query patterns
- Generate random job sets and criteria
- Verify properties hold for all generated inputs

Both approaches are complementary and necessary. Unit tests catch concrete bugs and document expected behavior. Property tests verify general correctness across the input space.

### Property-Based Testing Configuration

**Library Selection**: Use **jqwik** for Java property-based testing
- Mature, well-maintained library for Java
- Integrates with JUnit 5
- Supports custom generators and shrinking
- Better than implementing PBT from scratch

**Test Configuration**:
```java
@Property(tries = 100)  // Minimum 100 iterations per property
void propertyName(@ForAll Generator<Type> input) {
    // Property test implementation
}
```

**Test Tagging**: Each property test must reference its design property
```java
/**
 * Feature: java-native-implementation, Property 1: Cron Expression Round-Trip
 * 
 * For any valid cron expression, parsing then formatting then parsing again
 * SHALL produce an equivalent cron expression.
 */
@Property(tries = 100)
@Tag("property-test")
@Tag("cron-parser")
void cronExpressionRoundTrip(@ForAll("validCronExpressions") String cronExpr) {
    Cron parsed1 = cronParser.parse(cronExpr);
    String formatted = cronParser.format(parsed1);
    Cron parsed2 = cronParser.parse(formatted);
    
    assertThat(parsed2).isEqualTo(parsed1);
}
```

### Existing Test Migration Strategy

The current implementation has a comprehensive test suite that must be preserved or migrated:

**Tests to Keep Unchanged**:
- `CronQueryApiIntegrationTest` - API endpoint integration tests
  - These test the REST API contract which must remain identical
  - Should pass without modification after implementation
  - Validates API compatibility requirement
  
- `CrontabLoaderImplTest` - Crontab loader unit tests (8 tests)
  - CrontabLoader component is not changing
  - All tests should continue to pass as-is
  - No modifications needed

**Tests to Update**:
- `CronQueryServiceImplTest` - Service layer unit tests (14 tests)
  - Currently mocks GroovyJarAdapter
  - Update to mock CronJobService instead
  - Test logic remains the same, just swap the mocked dependency
  - Verify orchestration, error handling, metrics tracking

**Tests to Replace**:
- `GroovyJarAdapterImplTest` - Adapter unit tests (15 tests)
  - This tests the component being removed
  - Replace with new test classes:
    - `CronParserTest` - Test cron expression parsing and validation
    - `QueryParserTest` - Test natural language query parsing
    - `ScheduleAnalyzerTest` - Test job matching and next run calculation
    - `CronJobServiceTest` - Test orchestration workflow
  - Migrate test scenarios to appropriate new test class
  
- `GroovyJarIntegrationTest` - Groovy JAR integration tests (13 tests)
  - Replace with `JavaComponentIntegrationTest`
  - Test same scenarios but with new Java components
  - Verify end-to-end workflow without Groovy dependency

**Tests to Add**:
- Property-based tests using jqwik (completely new)
  - `CronParserPropertyTest` - Properties 1, 2
  - `QueryParserPropertyTest` - Properties 3, 4, 5, 6, 7
  - `ScheduleAnalyzerPropertyTest` - Properties 8, 9, 10, 11
  - `CronJobServicePropertyTest` - Properties 12, 15, 16
  - `ApiPropertyTest` - Properties 13, 14

**Test Migration Checklist**:
1. Run existing test suite to establish baseline (all should pass)
2. Implement new components with new unit tests
3. Update CronQueryServiceImplTest to use new mocks
4. Replace GroovyJarAdapterImplTest with new component tests
5. Replace GroovyJarIntegrationTest with JavaComponentIntegrationTest
6. Add property-based tests for all 16 properties
7. Run full test suite - all tests should pass
8. Verify 80% code coverage maintained or improved

### Test Coverage Requirements

**Minimum Coverage**: 80% code coverage across all components

**Component-Specific Tests**:

1. **CronParser**:
   - Unit: Valid expressions, invalid expressions, edge cases (Sunday as 0 vs 7)
   - Property: Round-trip, validation consistency

2. **QueryParser**:
   - Unit: Each supported pattern type, error cases, date conflicts
   - Property: Completeness, normalization idempotence, time format normalization

3. **ScheduleAnalyzer**:
   - Unit: Day matching, time matching, combined matching, next run calculation
   - Property: Matching correctness, next run count, format consistency

4. **CronJobService**:
   - Unit: Full workflow with mocked dependencies, error handling
   - Property: Response completeness, graceful degradation

5. **REST API**:
   - Integration: All endpoints with various query types
   - Property: HTTP status code mapping, API compatibility

### Test Data Generators

**Custom Generators for jqwik**:

```java
@Provide
Arbitrary<String> validCronExpressions() {
    return Combinators.combine(
        Arbitraries.integers().between(0, 59),  // minute
        Arbitraries.integers().between(0, 23),  // hour
        Arbitraries.integers().between(1, 31),  // day of month
        Arbitraries.integers().between(1, 12),  // month
        Arbitraries.integers().between(0, 6)    // day of week
    ).as((min, hr, dom, mon, dow) -> 
        String.format("%d %d %d %d %d", min, hr, dom, mon, dow)
    );
}

@Provide
Arbitrary<String> dayQueries() {
    return Arbitraries.of(
        "jobs on Monday", "jobs on Saturday", "jobs on weekends",
        "jobs on weekdays", "this Saturday", "next Monday",
        "jobs today", "jobs tomorrow"
    );
}

@Provide
Arbitrary<QueryCriteria> queryCriteria() {
    // Generate random QueryCriteria with various combinations
}
```

### Integration Testing

**Test Scenarios**:
1. End-to-end query processing with real crontab data
2. Multiple concurrent requests (thread safety)
3. Large crontab files (1000+ jobs) for performance validation
4. All output formats (JSON, CSV, YAML)
5. Health check endpoint with various system states

**Test Environment**:
- Use test_crontab.txt for consistent test data
- Mock CrontabLoader for controlled test scenarios
- Use TestRestTemplate for integration tests
- Verify metrics are properly recorded

### Edge Cases to Test

1. **Timezone Boundaries**: Jobs scheduled around midnight, DST transitions
2. **Leap Years**: February 29 in cron expressions
3. **Invalid Dates**: February 30, April 31
4. **Cron Edge Cases**: Both DOM and DOW specified (OR logic)
5. **Empty Results**: Queries that match no jobs
6. **Malformed Input**: Incomplete cron expressions, garbage queries
7. **Special Times**: "noon", "midnight" in queries
8. **Overnight Ranges**: "between 10 PM and 6 AM"


## Implementation Notes

### Migration Strategy

**Phase 1: Create New Components**
1. Implement CronParser with cron-utils integration
2. Implement QueryParser with all pattern support
3. Implement ScheduleAnalyzer with matching logic
4. Implement CronJobService to orchestrate components
5. Create comprehensive unit tests for each component

**Phase 2: Integration**
1. Update CronQueryServiceImpl to inject CronJobService
2. Replace GroovyJarAdapter calls with CronJobService calls
3. Update GlobalExceptionHandler to handle new exception types
4. Run integration tests to verify API compatibility

**Phase 3: Cleanup**
1. Remove GroovyJarAdapter interface and implementation
2. Remove Groovy JAR dependency from pom.xml
3. Remove maven-download-plugin configuration
4. Delete Groovy JAR files from lib/ directory
5. Remove Groovy-related imports

**Phase 4: Validation**
1. Run full test suite (unit + integration + property tests)
2. Verify all endpoints return identical responses to Groovy version
3. Performance testing with large crontab files
4. Health check validation

### Java 21 Features to Leverage

**Records**: Use for immutable data transfer objects
```java
public record QueryCriteria(...) { }
public record ParsedCronLine(...) { }
public record TimeRange(...) { }
```

**Pattern Matching**: Use for type checking and casting
```java
if (criteria instanceof QueryCriteria c && c.type() == QueryType.COMBINED) {
    // Use c directly
}
```

**Text Blocks**: Use for multi-line strings in tests
```java
String testCrontab = """
    0 8 * * 1-5 /usr/bin/backup.sh
    30 2 * * * /usr/bin/cleanup.sh
    """;
```

**Switch Expressions**: Use for query type handling
```java
return switch (criteria.type()) {
    case DAY_BASED -> matchDays(job, criteria);
    case TIME_BASED -> matchTime(job, criteria);
    case COMBINED -> matchDays(job, criteria) && matchTime(job, criteria);
    case UNKNOWN -> false;
};
```

**Stream API**: Use for collection processing
```java
return jobs.stream()
    .filter(job -> matches(job, criteria))
    .map(job -> enrichWithNextRuns(job))
    .toList();
```

**Optional**: Use for nullable return values
```java
public Optional<QueryCriteria> tryParse(String query) {
    try {
        return Optional.of(parse(query));
    } catch (InvalidQueryException e) {
        return Optional.empty();
    }
}
```

### Performance Considerations

**Caching Strategy**:
- Cache parsed Cron objects to avoid re-parsing same expressions
- Use ConcurrentHashMap for thread-safe caching
- Implement cache eviction for memory management

**Optimization Opportunities**:
- Parse crontab content once, reuse for multiple queries
- Parallel processing for large job lists using parallel streams
- Early termination when checking matches (short-circuit evaluation)

**Performance Targets**:
- Query execution: < 500ms for 1000 jobs
- Startup time: Comparable to or better than Groovy JAR
- Memory usage: No memory leaks during continuous operation

### Dependency Management

**Add to pom.xml**:
```xml
<!-- Cron parsing -->
<dependency>
    <groupId>com.cronutils</groupId>
    <artifactId>cron-utils</artifactId>
    <version>9.2.0</version>
</dependency>

<!-- Property-based testing -->
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.8.2</version>
    <scope>test</scope>
</dependency>
```

**Remove from pom.xml**:
```xml
<!-- Remove Groovy JAR system dependency -->
<dependency>
    <groupId>com.cronquery</groupId>
    <artifactId>cron-query-groovy</artifactId>
    <version>1.3.1</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/cron-query-groovy-1.3.1.jar</systemPath>
</dependency>

<!-- Remove maven-download-plugin -->
<plugin>
    <groupId>com.googlecode.maven-download-plugin</groupId>
    <artifactId>download-maven-plugin</artifactId>
    ...
</plugin>
```

### Configuration Preservation

All existing configuration remains unchanged:

**application.yml**: No changes required
- Crontab source configuration
- Server port and context path
- Actuator endpoints
- Logging configuration

**Metrics**: All existing metrics continue to work
- groovy_jar_invocations_total (rename to java_component_invocations_total)
- groovy_jar_execution_seconds (rename to java_component_execution_seconds)
- cron_query_errors_total (unchanged)

**Health Checks**: Update implementation but maintain contract
- Check CronJobService.validateCronExpression() instead of GroovyJarAdapter
- Return same HealthStatus structure

**OpenAPI/Swagger**: No changes required
- All endpoint signatures remain identical
- Request/response models unchanged


## Design Summary

### What Changes

**New Components**:
- `CronParser`: Validates and parses cron expressions using cron-utils
- `QueryParser`: Translates natural language to QueryCriteria
- `ScheduleAnalyzer`: Matches jobs and calculates next runs
- `CronJobService`: Orchestrates all components
- Exception hierarchy: CronParseException, InvalidQueryException, ScheduleAnalysisException
- Internal models: QueryCriteria (record), ParsedCronLine (record)

**Modified Components**:
- `CronQueryServiceImpl`: Inject CronJobService instead of GroovyJarAdapter
- `GlobalExceptionHandler`: Handle new exception types
- `pom.xml`: Add cron-utils and jqwik, remove Groovy JAR

**Removed Components**:
- `GroovyJarAdapter` interface and implementation
- Groovy JAR dependency and files
- maven-download-plugin configuration

### What Stays the Same

**Unchanged Components**:
- All REST controllers (CronQueryController)
- All models (QueryRequest, QueryResponse, CronJob, ErrorResponse, HealthStatus)
- CrontabLoader and implementation
- All configuration (application.yml, metrics, health checks)
- All OpenAPI/Swagger documentation
- Docker and Kubernetes configurations

**API Compatibility**:
- All endpoint paths and methods unchanged
- Request/response JSON structure identical
- HTTP status codes consistent
- Error response format preserved

### Key Design Decisions

1. **Use cron-utils Library**: Mature, well-tested library for cron parsing instead of implementing from scratch
2. **Java Records for DTOs**: Leverage Java 21 records for immutable data transfer objects
3. **jqwik for Property Testing**: Use established PBT library rather than custom implementation
4. **Graceful Degradation**: Continue processing when individual jobs fail to parse
5. **Component Separation**: Clear interfaces between parsing, analysis, and orchestration for testability
6. **Python Feature Parity**: Support all query patterns from Python implementation including explicit dates and conflict detection

### Risk Mitigation

**API Compatibility Risk**: Mitigated by comprehensive integration tests comparing responses
**Performance Risk**: Mitigated by performance testing with 1000+ job crontabs
**Feature Parity Risk**: Mitigated by property-based tests covering all query patterns
**Regression Risk**: Mitigated by maintaining existing test suite and adding new tests

### Success Criteria

The implementation is successful when:
1. All existing integration tests pass without modification
2. All new unit and property tests pass
3. Query processing completes in < 500ms for 1000 jobs
4. API responses are byte-for-byte identical to Groovy implementation
5. No Groovy dependencies remain in pom.xml
6. Code coverage exceeds 80%
7. All 16 correctness properties are validated by property-based tests

### Future Spring Boot Learning Opportunities (Next Spec)

**Note**: This spec focuses on replacing Groovy with Java and establishing solid component architecture. While this involves Spring Boot (dependency injection, testing, configuration), it doesn't deeply exercise Spring Boot-specific features. A future spec can focus on Spring Boot skill development:

**Additional REST Endpoints**:
- Job management endpoints (create, update, delete scheduled jobs)
- Bulk query operations
- Job history and execution tracking
- Advanced filtering and pagination
- File upload for crontab analysis

**Spring Data JPA**:
- Persist parsed cron jobs to database (H2 for dev, PostgreSQL for prod)
- Query history and analytics
- User preferences and saved queries
- Job execution history tracking
- Learn JPA repositories, entity relationships, transactions

**Spring Security**:
- Authentication (JWT, OAuth2)
- Authorization (role-based access control)
- API key management
- Rate limiting per user
- Secure endpoints practice

**Spring Validation**:
- Custom validators for cron expressions
- Request validation with @Valid
- Custom constraint annotations
- Validation groups for different scenarios

**Spring Boot Actuator**:
- Custom health indicators
- Custom metrics beyond basic counters
- Custom endpoints for admin operations
- Application info and build details

**Spring WebFlux** (Reactive):
- Convert to reactive endpoints
- Non-blocking cron analysis
- Server-Sent Events for real-time job monitoring
- Reactive database access

**Spring Batch**:
- Scheduled analysis jobs
- Batch processing of large crontab files
- Job scheduling and monitoring
- Learn chunk-oriented processing

**Advanced Configuration**:
- Profiles for different environments
- External configuration with Spring Cloud Config
- Feature flags
- Configuration properties validation

**Testing Enhancements**:
- @WebMvcTest for controller testing
- @DataJpaTest for repository testing
- @SpringBootTest with different configurations
- TestContainers for integration testing
- MockMvc for API testing

This phased approach allows focused learning: this spec establishes solid Java and testing fundamentals, future specs layer on Spring Boot ecosystem features. Each spec builds practical, interview-relevant skills.

