# Requirements Document

## Introduction

This document specifies requirements for a Spring Boot microservice implementation of cron-query. The microservice will wrap the existing Groovy implementation (distributed as a JAR) with REST endpoints, enabling HTTP-based access to cron schedule analysis. The implementation serves as a learning project for microservice architecture, Spring Boot framework, and containerization while providing practical resume-worthy experience.

## Glossary

- **CronQueryService**: The Spring Boot microservice application that provides REST API access to cron schedule analysis
- **GroovyJAR**: The existing cron-query Groovy implementation packaged as cron-query-groovy-1.2.2.jar
- **NaturalLanguageQuery**: A human-readable query string like "jobs on weekends" or "what runs at 8 AM"
- **StructuredQuery**: A query using explicit parameters like day, time, or time ranges
- **CrontabSource**: The source of cron job data (user crontab, system crontab, or Docker test file)
- **DockerImage**: A containerized version of CronQueryService with embedded test crontab data

## Requirements

### Requirement 1

**User Story:** As a developer, I want to query cron schedules via REST API, so that I can integrate cron analysis into web applications and services

#### Acceptance Criteria

1. WHEN a client sends a GET request to "/api/jobs" with a "query" parameter containing a natural language query, THE CronQueryService SHALL return a JSON response containing matching cron jobs
2. WHEN a client sends a GET request to "/api/jobs" with structured parameters (day, time, timeRange), THE CronQueryService SHALL return a JSON response containing matching cron jobs
3. THE CronQueryService SHALL support query parameters for output format (json, csv, yaml)
4. THE CronQueryService SHALL return HTTP 400 status code with error details when query parameters are invalid
5. THE CronQueryService SHALL return HTTP 500 status code with error details when crontab processing fails

### Requirement 2

**User Story:** As a system administrator, I want the microservice to access real crontab data, so that I can analyze actual scheduled jobs on my systems

#### Acceptance Criteria

1. WHEN deployed on a Linux system, THE CronQueryService SHALL read cron jobs from the user crontab
2. WHEN deployed on a Linux system, THE CronQueryService SHALL read cron jobs from /etc/crontab
3. WHEN deployed on a Linux system, THE CronQueryService SHALL read cron jobs from /etc/cron.d/* directories
4. THE CronQueryService SHALL continue processing remaining crontab sources when one source fails to load
5. THE CronQueryService SHALL log warnings when crontab sources are inaccessible due to permissions

### Requirement 3

**User Story:** As a developer, I want to reuse the existing Groovy implementation, so that I avoid reimplementing core logic and focus on microservice architecture

#### Acceptance Criteria

1. THE CronQueryService SHALL include the GroovyJAR as a Maven dependency
2. THE CronQueryService SHALL invoke GroovyJAR classes directly for query processing
3. THE CronQueryService SHALL handle exceptions from GroovyJAR and translate them to appropriate HTTP responses
4. THE CronQueryService SHALL pass query parameters to GroovyJAR in the expected format
5. THE CronQueryService SHALL transform GroovyJAR output into REST API response format

### Requirement 4

**User Story:** As a DevOps engineer, I want to deploy the microservice as a Docker container, so that I can run it consistently across different environments

#### Acceptance Criteria

1. THE CronQueryService SHALL provide a Dockerfile that builds a runnable container image
2. THE DockerImage SHALL include a test crontab file for validation purposes
3. THE DockerImage SHALL expose the service on a configurable port (default 8080)
4. THE DockerImage SHALL use a multi-stage build to minimize image size
5. THE DockerImage SHALL include health check configuration

### Requirement 5

**User Story:** As a monitoring system, I want to check service health and readiness, so that I can detect and respond to service issues

#### Acceptance Criteria

1. THE CronQueryService SHALL provide a GET endpoint at "/actuator/health" that returns service health status
2. WHEN the service is running and can access crontab sources, THE CronQueryService SHALL return HTTP 200 with status "UP"
3. THE CronQueryService SHALL provide a GET endpoint at "/actuator/info" that returns version and build information
4. THE CronQueryService SHALL expose Prometheus-compatible metrics at "/actuator/prometheus"
5. THE CronQueryService SHALL include metrics for request count, response time, and error rate

### Requirement 6

**User Story:** As a developer integrating with the API, I want comprehensive API documentation, so that I can understand available endpoints and parameters

#### Acceptance Criteria

1. THE CronQueryService SHALL provide OpenAPI/Swagger documentation at "/swagger-ui.html"
2. THE CronQueryService SHALL document all query parameters with descriptions and examples
3. THE CronQueryService SHALL provide example requests and responses for each endpoint
4. THE CronQueryService SHALL document error response formats and status codes
5. THE CronQueryService SHALL include a "/api-docs" endpoint that returns OpenAPI specification in JSON format

### Requirement 7

**User Story:** As a system administrator, I want to configure the service via environment variables or configuration files, so that I can customize behavior for different deployment environments

#### Acceptance Criteria

1. THE CronQueryService SHALL support configuration of server port via environment variable or application.yml
2. THE CronQueryService SHALL support configuration of crontab source paths via environment variable or application.yml
3. THE CronQueryService SHALL support configuration of log level via environment variable or application.yml
4. THE CronQueryService SHALL support configuration of CORS allowed origins via environment variable or application.yml
5. THE CronQueryService SHALL document all configuration options in README.md

### Requirement 8

**User Story:** As a developer, I want to test the microservice locally with mock data, so that I can develop on Windows without requiring Linux crontab access

#### Acceptance Criteria

1. WHEN the CronQueryService cannot access system crontab sources, THE CronQueryService SHALL fall back to a test crontab file
2. THE CronQueryService SHALL support loading crontab data from a file path specified in configuration
3. THE CronQueryService SHALL log which crontab sources are being used at startup
4. THE CronQueryService SHALL include a sample test crontab file in the project
5. THE CronQueryService SHALL document the test data approach in README.md
