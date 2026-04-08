# Implementation Plan: Java Native Implementation

## Overview

This plan replaces the Groovy JAR dependency with pure Java 21 components while maintaining full API compatibility. The implementation follows a four-phase approach: (1) Create new Java components with comprehensive tests, (2) Integrate components into Spring Boot service, (3) Remove Groovy dependencies, and (4) Validate complete system. Each component is implemented with both unit tests and property-based tests to ensure correctness.

The user will implement this by hand for learning purposes, adapting code from the existing Groovy implementation where applicable. Most Spring Boot infrastructure remains unchanged - only the Groovy adapter and its dependencies are replaced.

## Task Markers

- `[ ]` = **Required** - Must complete for spec to be done
- `[!]` = **Recommended** - Should do (learning opportunity, quality assurance)
- `[*]` = **Optional** - Nice-to-have enhancement, can skip

## Tasks

- [x] 0. Establish baseline and verify current system
  - Run existing test suite (mvn test)
  - Verify all tests pass (establish green baseline)
  - Document current test count and coverage
  - Verify application builds successfully (mvn clean compile)
  - Verify application runs (test endpoints if possible)
  - This confirms the starting point is healthy before making changes
  - _Requirements: All (baseline validation)_

- [x] 1. Set up project dependencies and exception hierarchy
  - **⚠️ LEARNING TASK - MANUAL IMPLEMENTATION REQUIRED**
    - **Skills to practice**: Maven dependency management, Java exception hierarchies
    - **Action required**: Implement this task manually, or explicitly ask Kiro to proceed
    - **Kiro will STOP here and wait for your decision**
  - Add cron-utils 9.2.0 dependency to pom.xml
  - Add jqwik 1.8.2 test dependency to pom.xml
  - Create exception classes: CronParseException, InvalidQueryException, ScheduleAnalysisException
  - Create base CronQueryException class
  - Add getters for context fields (invalidExpression, invalidQuery)
  - _Requirements: 2.2, 3.5, 8.1, 8.2, 8.3_

- [x] 2. Implement CronParser component
  - **⚠️ LEARNING TASK - MANUAL IMPLEMENTATION REQUIRED**
    - **Skills to practice**: Working with third-party libraries (cron-utils), interface design
    - **Action required**: Implement this task manually, or explicitly ask Kiro to proceed
    - **Kiro will STOP here and wait for your decision**
  - [x] 2.1 Create CronParser interface and implementation
    - Define interface with parse(), validate(), format() methods
    - Implement CronParserImpl using cron-utils library
    - Configure CronDefinition for Unix 5-field format
    - Handle special characters (*, -, /, ,, ?)
    - Support both 0 and 7 for Sunday in day-of-week
    - Throw CronParseException with descriptive messages for invalid expressions
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x]! 2.2 Write unit tests for CronParser
    - Test valid expressions (simple, ranges, lists, steps)
    - Test invalid expressions (out of range, malformed)
    - Test edge cases (Sunday as 0 vs 7, DOM/DOW OR logic)
    - Test validation method consistency
    - Test format method output
    - _Requirements: 9.1_

  - [ ]! 2.3 Write property test for round-trip consistency
    - **Property 1: Cron Expression Round-Trip**
    - **Validates: Requirements 2.7**
    - Generate random valid cron expressions
    - Verify parse → format → parse produces equivalent result
    - Use jqwik with custom cron expression generator
    - _Requirements: 2.7, 9.1_

  - [ ]! 2.4 Write property test for validation consistency
    - **Property 2: Cron Validation Consistency**
    - **Validates: Requirements 2.2, 2.3**
    - Generate random cron expression strings (valid and invalid)
    - Verify parse() success implies validate() returns true
    - Verify parse() throws exception implies validate() returns false
    - _Requirements: 2.2, 2.3, 9.1_

- [ ] 3. Implement QueryParser component
  - **⚠️ LEARNING TASK - MANUAL IMPLEMENTATION REQUIRED**
    - **Skills to practice**: Regex patterns, Java records, complex parsing logic
    - **Action required**: Implement this task manually, or explicitly ask Kiro to proceed
    - **Kiro will STOP here and wait for your decision**
  - [ ] 3.1 Create QueryCriteria record and supporting types
    - Define QueryCriteria record with type, days, times, date fields
    - Define QueryType enum (DAY_BASED, TIME_BASED, COMBINED, UNKNOWN)
    - Define TimeRange record with start, end, RangeType
    - Define RangeType enum (AFTER, BEFORE, BETWEEN)
    - Add helper methods: hasDayCriteria(), hasTimeCriteria(), isSpecificDate()
    - _Requirements: 3.7, 7.3_

  - [ ] 3.2 Create QueryParser interface and implementation
    - Define interface with parse() and normalize() methods
    - Implement regex patterns for day, time, and combined queries
    - Support day names (Monday, Saturday, weekends, weekdays)
    - Support relative dates (today, tomorrow, this Saturday, next Monday)
    - Support explicit dates (9/18/2025, 2025-09-18)
    - Support time formats (8 AM, 8:30 PM, noon, midnight, 08:00, 20:30)
    - Support time ranges (after 10 AM, before 5 PM, between 9 AM and 5 PM)
    - Support combined patterns (Saturday at 8 AM, weekdays between 9 AM and 5 PM)
    - Normalize queries by removing prefixes (which jobs run, show me jobs, find jobs)
    - Handle typos (comming → coming)
    - Validate explicit dates are valid calendar dates
    - Detect day-date conflicts (Saturday 9/18/2025 where date is not Saturday)
    - Throw InvalidQueryException with descriptive messages
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 13.8, 13.9, 13.10, 13.11_

  - [ ]! 3.3 Write unit tests for QueryParser
    - Test day-only queries (Saturday, weekends, weekdays)
    - Test time-only queries (8 AM, noon, 20:30)
    - Test time range queries (after, before, between)
    - Test combined queries (day + time, day + range)
    - Test relative dates (today, tomorrow, this Saturday)
    - Test explicit dates (9/18/2025, 2025-09-18)
    - Test day-date combinations and conflicts
    - Test query normalization (prefix removal)
    - Test invalid queries and error messages
    - Test edge cases (invalid dates, overnight ranges)
    - _Requirements: 9.2_

  - [ ]! 3.4 Write property test for parser completeness
    - **Property 3: Query Parser Completeness**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 10.1-10.6**
    - Generate queries with recognizable patterns
    - Verify QueryType is not UNKNOWN for valid patterns
    - Verify appropriate criteria fields are populated
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 9.2_

  - [ ]! 3.5 Write property test for normalization idempotence
    - **Property 4: Query Normalization Idempotence**
    - **Validates: Requirements 13.8**
    - Generate random query strings
    - Verify normalize(normalize(q)) equals normalize(q)
    - _Requirements: 13.8, 9.2_

  - [ ]! 3.6 Write property test for time format normalization
    - **Property 5: Time Format Normalization**
    - **Validates: Requirements 10.7**
    - Generate same time in different formats (12-hour, 24-hour, special names)
    - Verify all formats normalize to same LocalTime
    - Test: "8 PM", "20:00", "8:00 PM" all produce LocalTime.of(20, 0)
    - _Requirements: 10.7, 9.2_

  - [ ]! 3.7 Write property test for date validation
    - **Property 6: Date Validation**
    - **Validates: Requirements 13.10**
    - Generate invalid calendar dates (2/30/2025, 4/31/2025)
    - Verify InvalidQueryException is thrown
    - _Requirements: 13.10, 9.2_

  - [ ]! 3.8 Write property test for day-date conflict detection
    - **Property 7: Day-Date Conflict Detection**
    - **Validates: Requirements 13.3**
    - Generate queries with mismatched day names and dates
    - Verify InvalidQueryException with descriptive conflict message
    - _Requirements: 13.3, 9.2_

- [ ] 4. Checkpoint - Verify parsing components
  - Ensure all CronParser and QueryParser tests pass
  - Verify property-based tests run with 100+ iterations each
  - Check code coverage for parsing components (target 80%+)
  - Ask the user if questions arise

- [ ] 5. Implement ScheduleAnalyzer component
  - **⚠️ LEARNING TASK - MANUAL IMPLEMENTATION REQUIRED**
    - **Skills to practice**: Working with date/time APIs (java.time), complex matching logic
    - **Action required**: Implement this task manually, or explicitly ask Kiro to proceed
    - **Kiro will STOP here and wait for your decision**
  - [ ] 5.1 Create ScheduleAnalyzer interface and implementation
    - Define interface with findMatching(), calculateNextRuns(), matches() methods
    - Implement day-of-week matching using cron-utils ExecutionTime
    - Handle DOM/DOW OR logic correctly (job runs when EITHER matches)
    - Implement time matching for exact times and time ranges
    - Implement combined matching (day AND time criteria)
    - Implement specific date matching
    - Calculate next N execution times using ExecutionTime.nextExecution()
    - Format execution times as "yyyy-MM-dd HH:mm:ss z"
    - Use ZonedDateTime with system default timezone
    - Handle expressions that never execute again (return empty list)
    - Skip invalid cron expressions with warning log, continue processing
    - Use java.time APIs throughout (no Joda-Time)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 7.2, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_

  - [ ]! 5.2 Write unit tests for ScheduleAnalyzer
    - Test day-only matching (jobs on Saturday, weekends, weekdays)
    - Test time-only matching (jobs at 8 AM, jobs at noon)
    - Test time range matching (after, before, between)
    - Test combined matching (day + time)
    - Test specific date matching
    - Test next run calculation (count, ordering, format)
    - Test DOM/DOW OR logic edge cases
    - Test graceful handling of invalid expressions
    - Test timezone handling
    - Test edge cases (leap years, DST transitions, overnight ranges)
    - _Requirements: 9.3_

  - [ ]! 5.3 Write property test for schedule matching correctness
    - **Property 8: Schedule Matching Correctness**
    - **Validates: Requirements 4.1, 4.2, 4.3**
    - Generate random cron jobs and query criteria
    - For each match, verify at least one of next 100 executions satisfies criteria
    - Check day constraints match actual execution days
    - Check time constraints match actual execution times
    - _Requirements: 4.1, 4.2, 4.3, 9.3_

  - [ ]! 5.4 Write property test for next run count
    - **Property 9: Next Run Count**
    - **Validates: Requirements 4.5, 11.1**
    - Generate random valid cron expressions and counts N
    - Verify calculateNextRuns() returns exactly N results
    - Verify all results are after start time
    - Verify results are in chronological order
    - _Requirements: 4.5, 11.1, 9.3_

  - [ ]! 5.5 Write property test for next run format
    - **Property 10: Next Run Format**
    - **Validates: Requirements 11.3**
    - Generate random execution times
    - Verify formatted strings match "yyyy-MM-dd HH:mm:ss z" pattern
    - Verify strings can be parsed back to ZonedDateTime
    - _Requirements: 11.3, 9.3_

  - [ ]! 5.6 Write property test for graceful degradation
    - **Property 11: Graceful Degradation**
    - **Validates: Requirements 4.6, 8.4**
    - Generate job lists with mix of valid and invalid expressions
    - Verify findMatching() returns results from valid jobs
    - Verify no exception is thrown
    - Verify invalid jobs are logged but skipped
    - _Requirements: 4.6, 8.4, 9.3_

- [ ] 6. Implement CronJobService component
  - **⚠️ LEARNING TASK - MANUAL IMPLEMENTATION REQUIRED**
    - **Skills to practice**: Service orchestration, dependency injection patterns
    - **Action required**: Implement this task manually, or explicitly ask Kiro to proceed
    - **Kiro will STOP here and wait for your decision**
  - [ ] 6.1 Create ParsedCronLine record
    - Define record with cronExpression, command, user, source, lineNumber, isValid
    - Add factory methods: fromUserCrontab(), fromSystemCrontab()
    - Handle both 5-field (user) and 6-field (system) crontab formats
    - _Requirements: 5.4, 7.3_

  - [ ] 6.2 Create CronJobService interface and implementation
    - Define interface with executeQuery(), loadAllJobs(), validateCronExpression() methods
    - Inject CronParser, QueryParser, ScheduleAnalyzer dependencies
    - Implement executeQuery() workflow:
      - Parse natural language query or build criteria from structured params
      - Parse crontab content into ParsedCronLine objects
      - Skip empty lines and comments
      - Parse each cron expression using CronParser
      - Find matching jobs using ScheduleAnalyzer
      - Calculate next 3 run times for each match
      - Build CronJob model objects
      - Return list of matching jobs
    - Implement loadAllJobs() to parse all jobs from crontab
    - Implement validateCronExpression() using CronParser
    - Handle errors: log warnings for individual job failures, throw exceptions for complete failures
    - Translate component exceptions to service exceptions
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

  - [ ]! 6.3 Write unit tests for CronJobService
    - Test executeQuery() with natural language queries
    - Test executeQuery() with structured queries
    - Test loadAllJobs() with various crontab formats
    - Test validateCronExpression() with valid and invalid expressions
    - Test error handling (invalid queries, parse failures)
    - Test graceful degradation (some jobs fail, others succeed)
    - Test exception translation
    - Mock all dependencies (CronParser, QueryParser, ScheduleAnalyzer)
    - _Requirements: 9.4_

  - [ ]! 6.4 Write property test for response completeness
    - **Property 12: Query Response Completeness**
    - **Validates: Requirements 5.7**
    - Generate random successful query executions
    - Verify all required fields are non-null (jobs, totalCount, query, sources, executionTimeMs)
    - _Requirements: 5.7, 9.4_

  - [ ]! 6.5 Write property test for exception context preservation
    - **Property 15: Exception Context Preservation**
    - **Validates: Requirements 8.1, 8.2**
    - Generate invalid inputs (cron expressions, queries)
    - Verify thrown exceptions include the invalid input
    - Check exception message or field contains problematic input
    - _Requirements: 8.1, 8.2, 9.4_

  - [ ]! 6.6 Write property test for empty result handling
    - **Property 16: Empty Result Handling**
    - **Validates: Requirements 5.7**
    - Generate queries that match zero jobs
    - Verify response has empty jobs list and totalCount of 0
    - Verify other fields (query, sources, executionTimeMs) are still populated
    - _Requirements: 5.7, 9.4_

- [ ] 7. Checkpoint - Verify all components work in isolation
  - Ensure all component tests pass (CronParser, QueryParser, ScheduleAnalyzer, CronJobService)
  - Verify all 16 property-based tests pass with 100+ iterations
  - Check overall code coverage (target 80%+)
  - Ask the user if questions arise

- [ ] 8. Integrate components into Spring Boot service
  - **⚠️ LEARNING TASK - MANUAL IMPLEMENTATION REQUIRED**
    - **Skills to practice**: Spring Boot integration, refactoring existing code
    - **Action required**: Implement this task manually, or explicitly ask Kiro to proceed
    - **Kiro will STOP here and wait for your decision**
  - [ ] 8.1 Update CronQueryServiceImpl
    - Remove GroovyJarAdapter dependency injection
    - Add CronJobService dependency injection
    - Update executeQuery() to call cronJobService.executeQuery()
    - Update getAllJobs() to call cronJobService.loadAllJobs()
    - Update checkHealth() to call cronJobService.validateCronExpression()
    - Maintain all existing metrics tracking (invocation counter, execution timer)
    - Keep all existing error handling and logging
    - _Requirements: 5.1, 5.2, 5.3, 12.3_

  - [ ] 8.2 Update GlobalExceptionHandler
    - Add @ExceptionHandler for ScheduleAnalysisException (map to 500)
    - Keep existing handlers: InvalidQueryException (400), CrontabLoadException (500)
    - Remove @ExceptionHandler for GroovyJarException (no longer exists)
    - Maintain existing error response format (ErrorResponse model)
    - Maintain existing logging and metrics in handlers
    - _Requirements: 6.5, 6.6, 8.6_

  - [ ]! 8.3 Update CronQueryServiceImplTest
    - Replace GroovyJarAdapter mock with CronJobService mock
    - Update test setup to mock CronJobService methods
    - Verify all existing test scenarios still pass
    - Keep all test assertions unchanged (testing orchestration, not implementation)
    - _Requirements: 9.4_

  - [ ]! 8.4 Write integration tests for Java components
    - Create JavaComponentIntegrationTest (replaces GroovyJarIntegrationTest)
    - Test end-to-end workflow: query → parse → analyze → response
    - Test with real crontab data (test_crontab.txt)
    - Test all query patterns from requirements
    - Test error scenarios (invalid queries, parse failures)
    - Verify response structure matches expectations
    - Do NOT mock components - test real integration
    - _Requirements: 9.5_

  - [ ]! 8.5 Write API property tests
    - **Property 13: HTTP Status Code Mapping**
    - **Validates: Requirements 6.4, 6.5, 6.6**
    - Generate various request scenarios (success, invalid query, internal error)
    - Verify correct HTTP status codes (200, 400, 500)
    - _Requirements: 6.4, 6.5, 6.6, 9.5_

  - [ ]! 8.6 Write API compatibility property test
    - **Property 14: API Compatibility**
    - **Validates: Requirements 6.3**
    - Generate random QueryRequest objects
    - Execute against both old (if available) and new implementations
    - Verify JSON response structure is identical
    - Check field names, types, and nesting match
    - _Requirements: 6.3, 9.5_

- [ ] 9. Checkpoint - Verify Spring Boot integration
  - Ensure all integration tests pass
  - Verify API endpoints return correct responses
  - Check that existing CronQueryApiIntegrationTest still passes
  - Verify metrics and health checks work correctly
  - Ask the user if questions arise

- [ ] 10. Remove Groovy dependencies
  - **⚠️ LEARNING TASK - MANUAL IMPLEMENTATION REQUIRED**
    - **Skills to practice**: Dependency cleanup, build configuration
    - **Action required**: Implement this task manually, or explicitly ask Kiro to proceed
    - **Kiro will STOP here and wait for your decision**
  - [ ] 10.1 Remove Groovy JAR from pom.xml
    - Remove system dependency for cron-query-groovy
    - Remove maven-download-plugin configuration
    - Verify pom.xml has no Groovy references
    - _Requirements: 1.1, 1.2_

  - [ ] 10.2 Remove Groovy files and imports
    - Delete GroovyJarAdapter interface
    - Delete GroovyJarAdapterImpl class
    - Delete GroovyJarException class
    - Remove all imports of com.cronquery Groovy classes
    - Delete Groovy JAR files from lib/ directory
    - _Requirements: 1.3, 1.4_

  - [ ] 10.3 Clean up test files
    - Delete GroovyJarAdapterImplTest
    - Delete GroovyJarIntegrationTest
    - Remove any remaining Groovy-related test utilities
    - _Requirements: 1.3, 1.4_

  - [ ] 10.4 Verify clean build
    - Run mvn clean compile
    - Verify no compilation errors
    - Verify no missing dependency errors
    - _Requirements: 1.5_

- [ ] 11. Final validation and performance testing
  - **⚠️ LEARNING TASK - MANUAL IMPLEMENTATION REQUIRED**
    - **Skills to practice**: Comprehensive testing, performance validation
    - **Action required**: Implement this task manually, or explicitly ask Kiro to proceed
    - **Kiro will STOP here and wait for your decision**
  - [ ]! 11.1 Run complete test suite
    - Execute all unit tests (mvn test)
    - Execute all integration tests
    - Execute all property-based tests
    - Verify 100% pass rate
    - _Requirements: 9.6_

  - [ ]! 11.2 Verify code coverage
    - Generate coverage report (mvn jacoco:report)
    - Verify overall coverage is at least 80%
    - Check coverage for each component
    - _Requirements: 9.6_

  - [ ]* 11.3 Performance testing
    - Create test crontab with 1000 jobs
    - Execute various query types
    - Measure execution time for each query
    - Verify all queries complete in under 500ms
    - _Requirements: Performance.1_

  - [ ]! 11.4 API compatibility validation
    - Run CronQueryApiIntegrationTest
    - Verify all endpoints return expected responses
    - Compare response JSON structure with Groovy implementation
    - Verify no breaking changes
    - _Requirements: 6.1, 6.2, 6.3, 6.7, Compatibility.2_

  - [ ]! 11.5 Health check and metrics validation
    - Test /actuator/health endpoint
    - Test /actuator/metrics endpoints
    - Verify all metrics are recorded correctly
    - Test Prometheus endpoint if configured
    - _Requirements: 12.2, 12.3_

  - [ ]* 11.6 Configuration validation
    - Verify application.yml is unchanged
    - Test with different crontab sources
    - Verify logging configuration works
    - Test Docker build if applicable
    - _Requirements: 12.1, 12.6, 12.7_

- [ ] 12. Final checkpoint - Complete validation
  - All tests pass (unit, integration, property-based)
  - Code coverage exceeds 80%
  - Performance targets met (< 500ms for 1000 jobs)
  - API compatibility verified
  - No Groovy dependencies remain
  - Documentation updated if needed
  - Ask the user if questions arise

## Notes

- Tasks marked with `!` are recommended (learning opportunities, quality assurance)
- Tasks marked with `*` are optional enhancements (can skip for faster MVP)
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests validate universal correctness properties from the design
- Unit tests validate specific examples and edge cases
- The implementation can adapt code from the existing Groovy implementation
- Most Spring Boot infrastructure (controllers, models, config) remains unchanged
- Focus is on replacing GroovyJarAdapter with Java components
- All 16 correctness properties from the design should be validated by property-based tests
