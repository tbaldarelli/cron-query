# Requirements Document

## Introduction

This feature replaces the Groovy JAR dependency in the Spring Boot microservice with a pure Java implementation. The current architecture wraps a Groovy JAR that uses Java libraries (cron-utils, joda-time) for cron parsing and schedule analysis. This replacement will eliminate the Groovy dependency, modernize the codebase with Java 21 features and java.time APIs, and provide a learning opportunity for practicing Spring Boot patterns.

The implementation will maintain full API compatibility with the existing REST endpoints while improving maintainability and reducing dependency complexity.

## Glossary

- **Spring_Boot_Service**: The microservice application that exposes REST APIs for cron job querying
- **Groovy_JAR**: The existing cron-query-groovy JAR file containing Groovy implementations
- **Java_Implementation**: The new pure Java components that will replace Groovy_JAR functionality
- **CronParser**: Component responsible for parsing and validating cron expressions
- **QueryParser**: Component that translates natural language queries into structured criteria
- **ScheduleAnalyzer**: Component that matches cron jobs against query criteria and calculates next run times
- **CronJobService**: Orchestration service that coordinates parsing, analysis, and response generation
- **REST_API**: The HTTP endpoints exposed by Spring_Boot_Service
- **cron-utils**: Third-party Java library for cron expression parsing and execution time calculation
- **Natural_Language_Query**: User-provided text like "jobs on Saturday" or "jobs at 8 AM"
- **Structured_Query**: Query parameters like day, time, or timeRange provided separately
- **Crontab_Content**: Raw text containing cron job definitions in standard crontab format

## Requirements

### Requirement 1: Remove Groovy Dependency

**User Story:** As a developer, I want to remove the Groovy JAR dependency from the project, so that the codebase uses only Java and reduces complexity.

#### Acceptance Criteria

1. THE Spring_Boot_Service SHALL remove the Groovy JAR system dependency from pom.xml
2. THE Spring_Boot_Service SHALL remove the maven-download-plugin configuration that fetches the Groovy JAR
3. THE Spring_Boot_Service SHALL remove all Groovy JAR files from the lib/ directory
4. THE Spring_Boot_Service SHALL remove all import statements referencing com.cronquery Groovy classes
5. THE Spring_Boot_Service SHALL compile successfully without the Groovy JAR dependency

### Requirement 2: Parse Cron Expressions

**User Story:** As a developer, I want to parse and validate cron expressions using pure Java, so that the service can understand cron schedules without Groovy.

#### Acceptance Criteria

1. THE CronParser SHALL parse valid 5-field cron expressions (minute hour day-of-month month day-of-week)
2. WHEN an invalid cron expression is provided, THE CronParser SHALL return a descriptive error message
3. THE CronParser SHALL validate cron expressions and return true for valid expressions and false for invalid ones
4. THE CronParser SHALL use the cron-utils library for parsing and validation
5. THE CronParser SHALL handle special characters in cron expressions (asterisk, comma, hyphen, slash, question mark)
6. THE CronParser SHALL correctly interpret the OR logic between day-of-month and day-of-week fields
7. FOR ALL valid cron expressions, parsing then formatting then parsing SHALL produce an equivalent expression (round-trip property)

### Requirement 3: Parse Natural Language Queries

**User Story:** As a user, I want to query cron jobs using natural language, so that I can find jobs without knowing cron syntax.

#### Acceptance Criteria

1. WHEN a Natural_Language_Query contains day references, THE QueryParser SHALL extract day criteria (e.g., "Saturday", "weekends", "Monday")
2. WHEN a Natural_Language_Query contains time references, THE QueryParser SHALL extract time criteria (e.g., "8 AM", "10:30 PM", "noon")
3. WHEN a Natural_Language_Query contains time ranges, THE QueryParser SHALL extract start and end times (e.g., "between 9 AM and 5 PM")
4. WHEN a Natural_Language_Query combines day and time, THE QueryParser SHALL extract both criteria (e.g., "Saturday at 8 AM")
5. WHEN a Natural_Language_Query cannot be understood, THE QueryParser SHALL return an error indicating the query is invalid
6. THE QueryParser SHALL support relative day references (e.g., "today", "tomorrow", "this Saturday")
7. THE QueryParser SHALL convert parsed criteria into a QueryCriteria object with day and time filters

### Requirement 4: Analyze Cron Schedules

**User Story:** As a user, I want to find cron jobs that match my query criteria, so that I can understand which jobs will run at specific times.

#### Acceptance Criteria

1. WHEN query criteria specify days, THE ScheduleAnalyzer SHALL return only jobs that run on those days
2. WHEN query criteria specify times, THE ScheduleAnalyzer SHALL return only jobs that run at those times
3. WHEN query criteria specify both days and times, THE ScheduleAnalyzer SHALL return only jobs matching both conditions
4. THE ScheduleAnalyzer SHALL correctly handle the OR logic between day-of-month and day-of-week in cron expressions
5. THE ScheduleAnalyzer SHALL calculate the next N execution times for each matching job
6. WHEN a cron expression is invalid, THE ScheduleAnalyzer SHALL skip that job and continue processing others
7. THE ScheduleAnalyzer SHALL use java.time APIs (ZonedDateTime, LocalDateTime) instead of Joda-Time

### Requirement 5: Orchestrate Query Processing

**User Story:** As a developer, I want a service that coordinates all components, so that query processing follows a clear workflow.

#### Acceptance Criteria

1. THE CronJobService SHALL accept QueryRequest objects containing either natural language or structured queries
2. WHEN a Natural_Language_Query is provided, THE CronJobService SHALL use QueryParser to convert it to QueryCriteria
3. WHEN structured query parameters are provided, THE CronJobService SHALL build QueryCriteria directly
4. THE CronJobService SHALL parse all cron jobs from Crontab_Content using CronParser
5. THE CronJobService SHALL use ScheduleAnalyzer to find jobs matching the QueryCriteria
6. THE CronJobService SHALL convert matching jobs to CronJob model objects with next run times
7. THE CronJobService SHALL return QueryResponse objects with jobs, count, query string, sources, and execution time
8. WHEN any component fails, THE CronJobService SHALL translate exceptions to appropriate service exceptions

### Requirement 6: Maintain REST API Compatibility

**User Story:** As an API consumer, I want the REST endpoints to work exactly as before, so that my existing integrations continue functioning.

#### Acceptance Criteria

1. THE REST_API SHALL accept POST requests to /api/query with QueryRequest JSON bodies
2. THE REST_API SHALL accept GET requests to /api/jobs to retrieve all jobs
3. THE REST_API SHALL return QueryResponse JSON with the same structure as the current implementation
4. THE REST_API SHALL return HTTP 200 for successful queries
5. THE REST_API SHALL return HTTP 400 for invalid queries with ErrorResponse JSON
6. THE REST_API SHALL return HTTP 500 for internal errors with ErrorResponse JSON
7. THE REST_API SHALL support all existing query parameters (query, day, time, timeRange, format)

### Requirement 7: Use Modern Java Features

**User Story:** As a developer, I want to use Java 21 features and modern APIs, so that the code is maintainable and follows current best practices.

#### Acceptance Criteria

1. THE Java_Implementation SHALL use Java 21 as the target version
2. THE Java_Implementation SHALL use java.time APIs (ZonedDateTime, LocalDateTime, Duration) instead of Joda-Time
3. THE Java_Implementation SHALL use record classes for immutable data transfer objects where appropriate
4. THE Java_Implementation SHALL use pattern matching for instanceof where applicable
5. THE Java_Implementation SHALL use text blocks for multi-line string literals where appropriate
6. THE Java_Implementation SHALL use Stream API for collection processing
7. THE Java_Implementation SHALL use Optional for nullable return values where appropriate

### Requirement 8: Handle Errors Gracefully

**User Story:** As a user, I want clear error messages when something goes wrong, so that I can understand and fix the problem.

#### Acceptance Criteria

1. WHEN a cron expression cannot be parsed, THE CronParser SHALL throw CronParseException with the invalid expression and reason
2. WHEN a query cannot be understood, THE QueryParser SHALL throw InvalidQueryException with the problematic query text
3. WHEN schedule analysis fails, THE ScheduleAnalyzer SHALL throw ScheduleAnalysisException with context about the failure
4. WHEN a single cron job fails to parse, THE CronJobService SHALL log a warning and continue processing other jobs
5. WHEN all cron jobs fail to parse, THE CronJobService SHALL throw CrontabLoadException
6. THE Spring_Boot_Service SHALL translate all exceptions to appropriate HTTP status codes and ErrorResponse objects
7. THE Spring_Boot_Service SHALL log all errors with sufficient context for debugging

### Requirement 9: Maintain Test Coverage

**User Story:** As a developer, I want comprehensive tests for all components, so that I can refactor confidently and catch regressions.

#### Acceptance Criteria

1. THE CronParser SHALL have unit tests covering valid expressions, invalid expressions, and edge cases
2. THE QueryParser SHALL have unit tests covering all supported query patterns and error cases
3. THE ScheduleAnalyzer SHALL have unit tests covering day matching, time matching, combined matching, and next run calculation
4. THE CronJobService SHALL have unit tests covering the full orchestration workflow with mocked dependencies
5. THE REST_API SHALL have integration tests covering all endpoints with various query types
6. THE Java_Implementation SHALL maintain at least 80% code coverage
7. THE Java_Implementation SHALL include tests for error handling and edge cases (empty crontab, malformed entries, timezone boundaries)

### Requirement 10: Support All Query Patterns

**User Story:** As a user, I want to query jobs using various patterns, so that I can find exactly what I need.

#### Acceptance Criteria

1. THE QueryParser SHALL support day-only queries (e.g., "jobs on Saturday", "jobs on weekends")
2. THE QueryParser SHALL support time-only queries (e.g., "jobs at 8 AM", "jobs at noon")
3. THE QueryParser SHALL support time range queries (e.g., "jobs between 9 AM and 5 PM")
4. THE QueryParser SHALL support combined queries (e.g., "jobs on Saturday at 8 AM", "jobs on weekdays between 9 AM and 5 PM")
5. THE QueryParser SHALL support relative date queries (e.g., "jobs today", "jobs tomorrow", "jobs this Saturday")
6. THE QueryParser SHALL support multiple day specifications (e.g., "jobs on Monday and Friday")
7. THE QueryParser SHALL normalize time formats (12-hour with AM/PM, 24-hour, special times like "noon" and "midnight")

### Requirement 11: Calculate Next Run Times

**User Story:** As a user, I want to see when each job will run next, so that I can plan around scheduled tasks.

#### Acceptance Criteria

1. THE ScheduleAnalyzer SHALL calculate the next 3 execution times for each cron job by default
2. THE ScheduleAnalyzer SHALL use the current system time as the starting point for calculations
3. THE ScheduleAnalyzer SHALL return execution times as formatted strings in "yyyy-MM-dd HH:mm:ss z" format
4. THE ScheduleAnalyzer SHALL handle timezone information correctly using system default timezone
5. WHEN a cron expression will never execute again, THE ScheduleAnalyzer SHALL return an empty list
6. THE ScheduleAnalyzer SHALL use cron-utils library for accurate next run calculations
7. THE ScheduleAnalyzer SHALL handle edge cases like leap years and daylight saving time transitions

### Requirement 12: Preserve Existing Configuration

**User Story:** As a developer, I want to keep the existing Spring Boot configuration, so that deployment and monitoring remain unchanged.

#### Acceptance Criteria

1. THE Spring_Boot_Service SHALL maintain all existing application.yml configuration properties
2. THE Spring_Boot_Service SHALL maintain all existing Actuator endpoints (/actuator/health, /actuator/metrics, /actuator/prometheus)
3. THE Spring_Boot_Service SHALL maintain all existing Micrometer metrics (query count, execution time, error count)
4. THE Spring_Boot_Service SHALL maintain all existing OpenAPI/Swagger documentation at /swagger-ui.html
5. THE Spring_Boot_Service SHALL maintain all existing health check logic in HealthStatus
6. THE Spring_Boot_Service SHALL maintain all existing Docker and Kubernetes configurations
7. THE Spring_Boot_Service SHALL maintain the same logging configuration and log levels

### Requirement 13: Achieve Python Feature Parity

**User Story:** As a user, I want the Java implementation to support all query patterns that the Python version supports, so that I have consistent functionality across implementations.

#### Acceptance Criteria

1. THE QueryParser SHALL support explicit date formats (e.g., "9/18/2025", "2025-09-18")
2. THE QueryParser SHALL support combined day and explicit date queries (e.g., "Saturday 9/18/2025")
3. WHEN a day name and explicit date conflict, THE QueryParser SHALL return a descriptive error (e.g., "9/18/2025 is a Thursday, not a Saturday")
4. THE QueryParser SHALL support relative date modifiers including "this", "next", "coming" (e.g., "this Saturday", "next Monday")
5. THE QueryParser SHALL handle common typos like "comming" as equivalent to "coming"
6. THE QueryParser SHALL support time range queries with "after", "before", and "between" keywords
7. THE QueryParser SHALL support combined day and time range queries (e.g., "this Saturday after 10 AM", "weekdays between 9 AM and 5 PM")
8. THE QueryParser SHALL normalize queries by removing common prefixes like "which jobs run", "show me jobs that run", "find jobs that run"
9. THE QueryParser SHALL support basic time references: "today", "tomorrow", "yesterday"
10. THE QueryParser SHALL validate that explicit dates are valid calendar dates (e.g., reject "2/30/2025")
11. THE QueryParser SHALL support both 12-hour format (with AM/PM) and 24-hour format for time specifications
12. THE QueryParser SHALL generate human-readable descriptions of parsed criteria matching Python's format_criteria_description output

## Non-Functional Requirements

### Performance

1. THE Java_Implementation SHALL process queries in under 500ms for crontabs with up to 1000 jobs
2. THE Java_Implementation SHALL have startup time comparable to or better than the Groovy JAR implementation
3. THE Java_Implementation SHALL use memory efficiently, with no memory leaks during continuous operation

### Maintainability

1. THE Java_Implementation SHALL follow Spring Boot best practices for service layer architecture
2. THE Java_Implementation SHALL use dependency injection for all component dependencies
3. THE Java_Implementation SHALL include Javadoc comments for all public interfaces and complex methods
4. THE Java_Implementation SHALL follow consistent naming conventions matching existing Spring Boot code

### Compatibility

1. THE Java_Implementation SHALL maintain backward compatibility with all existing REST API contracts
2. THE Java_Implementation SHALL produce identical JSON responses to the Groovy JAR implementation for the same queries
3. THE Java_Implementation SHALL support the same cron expression formats as the Groovy JAR implementation
