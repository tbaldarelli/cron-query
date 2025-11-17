# Implementation Plan

- [x] 1. Set up Spring Boot project structure and Maven configuration




  - Create project directory structure under `src/java/cron-query-service/`
  - Initialize Maven POM with Spring Boot parent, dependencies, and plugins
  - Configure Maven download plugin to fetch Groovy JAR from GitHub releases
  - Set up system scope dependency for Groovy JAR
  - Create basic Spring Boot application class
  - _Requirements: 3.1, 7.1_

- [x] 2. Implement data models and DTOs




  - [x] 2.1 Create request models


    - Implement `QueryRequest` class with query parameters
    - Implement `QueryCriteria` internal model for Groovy JAR integration
    - Add validation annotations
    - _Requirements: 1.1, 1.2_

  - [x] 2.2 Create response models


    - Implement `QueryResponse` class with job list and metadata
    - Implement `CronJob` class with schedule and execution details
    - Implement `ErrorResponse` class for error handling
    - Implement `HealthStatus` class for health checks
    - _Requirements: 1.1, 5.1, 5.2_

- [x] 3. Implement exception hierarchy and global error handling




  - [x] 3.1 Create custom exception classes


    - Implement `CronQueryException` base exception
    - Implement `InvalidQueryException` for client errors
    - Implement `CrontabLoadException` for crontab access failures
    - Implement `GroovyJarException` for Groovy JAR integration errors
    - _Requirements: 1.4, 1.5_

  - [x] 3.2 Implement global exception handler


    - Create `GlobalExceptionHandler` with `@ControllerAdvice`
    - Map exceptions to appropriate HTTP status codes
    - Format error responses consistently
    - Add logging for exceptions
    - _Requirements: 1.4, 1.5_

- [x] 4. Implement crontab loading functionality





  - [x] 4.1 Create CrontabLoader interface and implementation


    - Define `CrontabLoader` interface
    - Implement `CrontabLoaderImpl` with multi-source loading
    - Add logic to attempt user crontab, system crontab, and cron.d directories
    - Implement fallback to test file when system sources unavailable
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 8.1, 8.2_

  - [x] 4.2 Add configuration support for crontab sources


    - Create configuration properties class for crontab sources
    - Support environment variable overrides
    - Add logging for active sources at startup
    - _Requirements: 2.5, 7.2, 8.3_

- [x] 5. Implement Groovy JAR integration



  - [x] 5.1 Investigate Groovy JAR structure and API


    - Extract and examine Groovy JAR classes
    - Identify public API methods for query execution
    - Document integration approach (direct invocation vs process execution)
    - _Requirements: 3.1, 3.2_

  - [x] 5.2 Create GroovyJarAdapter interface and implementation


    - Define `GroovyJarAdapter` interface
    - Implement `GroovyJarAdapterImpl` with direct class invocation
    - Convert between API models and Groovy models
    - Handle Groovy-specific exceptions
    - _Requirements: 3.2, 3.3, 3.4, 3.5_

- [x] 6. Implement service layer




  - Create `CronQueryService` interface
  - Implement `CronQueryServiceImpl` with business logic
  - Orchestrate crontab loading and Groovy JAR invocation
  - Transform Groovy results to API response format
  - Add execution time tracking
  - _Requirements: 1.1, 1.2, 3.4, 3.5_

- [x] 7. Implement REST API controller



  - [x] 7.1 Create CronQueryController with GET endpoint


    - Implement `GET /api/jobs` endpoint
    - Support natural language query parameter
    - Support structured query parameters (day, time, timeRange)
    - Support format parameter (json, csv, yaml)
    - Add OpenAPI/Swagger annotations
    - _Requirements: 1.1, 1.2, 1.3, 6.2_

  - [x] 7.2 Implement response formatting


    - Add logic to format responses based on format parameter
    - Support JSON (default), CSV, and YAML output
    - _Requirements: 1.3_

- [x] 8. Configure Spring Boot Actuator and observability




  - [x] 8.1 Enable and configure Actuator endpoints


    - Add Actuator dependency
    - Configure health, info, and prometheus endpoints
    - Expose endpoints in application.yml
    - _Requirements: 5.1, 5.3, 5.4_

  - [x] 8.2 Implement custom health indicators


    - Create custom health indicator for crontab loader
    - Create custom health indicator for Groovy JAR integration
    - Include source information and job count in health details
    - _Requirements: 5.2_

  - [x] 8.3 Configure metrics


    - Add Micrometer dependency
    - Configure Prometheus metrics export
    - Add custom metrics for requests, Groovy JAR invocations, and errors
    - _Requirements: 5.5_

- [x] 9. Configure OpenAPI/Swagger documentation




  - Add SpringDoc OpenAPI dependency
  - Create `OpenApiConfig` configuration class
  - Add API metadata (title, version, description)
  - Document all endpoints with examples
  - Document error responses
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 10. Create application configuration files





  - [x] 10.1 Create base application.yml


    - Configure server port with environment variable support
    - Configure crontab sources
    - Configure logging levels
    - Configure Actuator endpoints
    - Configure OpenAPI settings
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 10.2 Create profile-specific configurations


    - Create `application-dev.yml` for development (uses test file)
    - Create `application-prod.yml` for production (uses system crontabs)
    - _Requirements: 7.1, 7.2, 7.3, 8.1_

  - [x] 10.3 Add CORS configuration


    - Create CORS configuration class
    - Support configurable allowed origins
    - _Requirements: 7.4_

- [x] 11. Create Dockerfile and Docker Compose configuration





  - [x] 11.1 Create multi-stage Dockerfile


    - Implement build stage with Maven and JDK
    - Implement runtime stage with JRE
    - Copy application JAR and test crontab file
    - Configure health check
    - Expose port 8080
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_


  - [x] 11.2 Create docker-compose.yml

    - Configure service with build context
    - Map ports and environment variables
    - Add volume mount for test crontab
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 12. Copy test crontab file to resources



  - Copy `test_crontab.txt` from project root to `src/main/resources/`
  - Copy to `src/test/resources/` for testing
  - _Requirements: 8.4_

- [x] 13. Create README documentation



  - Document project overview and purpose
  - Document API endpoints with examples
  - Document configuration options
  - Document build and run instructions
  - Document Docker deployment steps
  - Document CentOS deployment steps
  - Document test data approach
  - _Requirements: 7.5, 8.5_

- [ ]* 14. Write unit tests
  - [ ]* 14.1 Write controller tests
    - Test request validation
    - Test response formatting
    - Test error handling
    - Mock service layer
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [ ]* 14.2 Write service layer tests
    - Test business logic
    - Test exception translation
    - Test response transformation
    - Mock Groovy JAR adapter and crontab loader
    - _Requirements: 1.1, 1.2, 3.3, 3.4, 3.5_

  - [ ]* 14.3 Write integration layer tests
    - Test crontab loading with fallback logic
    - Test Groovy JAR adapter
    - Test configuration handling
    - _Requirements: 2.4, 2.5, 3.2, 3.3, 8.1, 8.2_

- [ ]* 15. Write integration tests
  - [ ]* 15.1 Write API integration tests
    - Use `@SpringBootTest` with random port
    - Test full request/response cycle
    - Test different query types
    - Test different output formats
    - Use test crontab file
    - _Requirements: 1.1, 1.2, 1.3_

  - [ ]* 15.2 Write Groovy JAR integration tests
    - Test actual Groovy JAR invocation
    - Verify query translation
    - Test error scenarios
    - _Requirements: 3.2, 3.3, 3.4_

- [x] 16. Build and test locally





  - Run Maven build to download Groovy JAR
  - Build application JAR
  - Run application with test profile
  - Test API endpoints manually
  - Verify health checks and metrics
  - Verify Swagger UI
  - _Requirements: All_

- [ ] 17. Build and test Docker image
  - Build Docker image
  - Run container locally
  - Test API endpoints in container
  - Test with volume-mounted crontab file
  - Verify health checks work in container
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
