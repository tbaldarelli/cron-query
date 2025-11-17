# Design Document

## Overview

The Spring Boot microservice wraps the existing cron-query Groovy implementation with a REST API layer. The architecture follows standard Spring Boot patterns with clear separation between web, service, and integration layers. The Groovy JAR is integrated as a Maven dependency and invoked directly through its public API, avoiding the overhead of shell execution.

The service is designed to be stateless, containerizable, and production-ready with observability features (health checks, metrics, API documentation). It supports both real crontab access on Linux systems and test data for development on Windows.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Docker Container                      │
│  ┌───────────────────────────────────────────────────┐  │
│  │           Spring Boot Application                  │  │
│  │                                                     │  │
│  │  ┌──────────────┐      ┌──────────────┐          │  │
│  │  │   REST API   │      │   Actuator   │          │  │
│  │  │  Controller  │      │  Endpoints   │          │  │
│  │  └──────┬───────┘      └──────────────┘          │  │
│  │         │                                          │  │
│  │  ┌──────▼───────────────────────────┐            │  │
│  │  │     CronQueryService             │            │  │
│  │  │  (Business Logic Layer)          │            │  │
│  │  └──────┬───────────────────────────┘            │  │
│  │         │                                          │  │
│  │  ┌──────▼───────────┐  ┌──────────────────┐     │  │
│  │  │  GroovyJAR       │  │  CrontabLoader   │     │  │
│  │  │  Integration     │  │  (File/System)   │     │  │
│  │  └──────────────────┘  └──────────────────┘     │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Component Layers

1. **REST API Layer** (`controller` package)
   - Handles HTTP requests/responses
   - Input validation and parameter parsing
   - Error handling and HTTP status mapping
   - OpenAPI/Swagger annotations

2. **Service Layer** (`service` package)
   - Business logic orchestration
   - Integration with Groovy JAR
   - Response transformation
   - Exception handling

3. **Integration Layer** (`integration` package)
   - Groovy JAR wrapper classes
   - Crontab data loading (system vs test file)
   - Configuration management

4. **Model Layer** (`model` package)
   - Request/Response DTOs
   - Error response models
   - API documentation models

## Components and Interfaces

### REST Controller

**CronQueryController**
- Endpoint: `GET /api/jobs`
- Query Parameters:
  - `query` (String, optional): Natural language query
  - `day` (String, optional): Day name or date
  - `time` (String, optional): Time in HH:MM format
  - `timeRange` (String, optional): Time range like "08:00-17:00"
  - `format` (String, optional): Output format (json, csv, yaml), default: json
- Response: `QueryResponse` with list of matching jobs
- Error Responses: `ErrorResponse` with details

**ActuatorEndpoints** (Spring Boot Actuator)
- `/actuator/health`: Health check
- `/actuator/info`: Build and version info
- `/actuator/prometheus`: Prometheus metrics

**SwaggerUI**
- `/swagger-ui.html`: Interactive API documentation
- `/api-docs`: OpenAPI JSON specification

### Service Layer

**CronQueryService**
```java
public interface CronQueryService {
    QueryResponse executeQuery(QueryRequest request);
    List<CronJob> getAllJobs();
    HealthStatus checkHealth();
}
```

**CronQueryServiceImpl**
- Orchestrates query execution
- Calls GroovyJAR integration
- Transforms results to API format
- Handles exceptions and logging

### Integration Layer

**GroovyJarAdapter**
```java
public interface GroovyJarAdapter {
    List<CronJob> queryJobs(QueryCriteria criteria);
    List<CronJob> loadAllJobs();
}
```

**GroovyJarAdapterImpl**
- Invokes Groovy JAR classes directly
- Converts between API models and Groovy models
- Handles Groovy-specific exceptions

**CrontabLoader**
```java
public interface CrontabLoader {
    String loadCrontabData();
    List<String> getActiveSources();
}
```

**CrontabLoaderImpl**
- Attempts to load from system sources (user crontab, /etc/crontab, /etc/cron.d/*)
- Falls back to test file if system sources unavailable
- Logs which sources are used
- Configurable via application properties

### Configuration

**application.yml**
```yaml
server:
  port: ${SERVER_PORT:8080}

cronquery:
  crontab:
    sources:
      - type: user
        enabled: true
      - type: system
        path: /etc/crontab
        enabled: true
      - type: directory
        path: /etc/cron.d
        enabled: true
      - type: file
        path: ${CRONTAB_TEST_FILE:test_crontab.txt}
        enabled: true
        fallback: true
  groovy-jar:
    version: 1.2.2

spring:
  application:
    name: cron-query-service
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

## Data Models

### Request Models

**QueryRequest**
```java
public class QueryRequest {
    private String query;           // Natural language query
    private String day;             // Day filter
    private String time;            // Time filter
    private String timeRange;       // Time range filter
    private OutputFormat format;    // json, csv, yaml
}
```

**QueryCriteria** (Internal model for Groovy JAR)
```java
public class QueryCriteria {
    private List<String> days;
    private TimeRange timeRange;
    private String rawQuery;
}
```

### Response Models

**QueryResponse**
```java
public class QueryResponse {
    private List<CronJob> jobs;
    private int totalCount;
    private String query;
    private List<String> sources;
    private long executionTimeMs;
}
```

**CronJob**
```java
public class CronJob {
    private String schedule;        // Cron expression
    private String command;         // Command to execute
    private String source;          // Source file
    private String user;            // User (if applicable)
    private List<String> nextRuns;  // Next execution times
}
```

**ErrorResponse**
```java
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private long timestamp;
}
```

### Health Check Model

**HealthStatus**
```java
public class HealthStatus {
    private String status;          // UP, DOWN, DEGRADED
    private Map<String, Object> details;
    private List<String> availableSources;
}
```

## Groovy JAR Integration Strategy

### Maven Dependency Configuration

**GitHub Release Download Strategy (Recommended)**

The Groovy JAR will be automatically downloaded from GitHub releases during the Maven build process. This approach:
- Stays synchronized with project version bumps
- Works seamlessly in CI/CD pipelines
- Eliminates manual JAR management
- Uses GitHub releases as single source of truth

**Maven Download Plugin Configuration:**
```xml
<plugin>
    <groupId>com.googlecode.maven-download-plugin</groupId>
    <artifactId>download-maven-plugin</artifactId>
    <version>1.9.0</version>
    <executions>
        <execution>
            <id>download-groovy-jar</id>
            <phase>initialize</phase>
            <goals>
                <goal>wget</goal>
            </goals>
            <configuration>
                <url>https://github.com/tbaldarelli/cron-query/releases/download/v${project.version}/cron-query-groovy-${project.version}.jar</url>
                <outputDirectory>${project.basedir}/lib</outputDirectory>
                <outputFileName>cron-query-groovy-${project.version}.jar</outputFileName>
                <skipCache>false</skipCache>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**System Scope Dependency:**
```xml
<dependency>
    <groupId>com.cronquery</groupId>
    <artifactId>cron-query-groovy</artifactId>
    <version>${project.version}</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/cron-query-groovy-${project.version}.jar</systemPath>
</dependency>
```

**Version Synchronization:**
- The `${project.version}` property ensures the Spring Boot service and Groovy JAR versions stay aligned
- When `.bumpversion.cfg` updates the project version, the POM automatically references the correct JAR
- CI/CD builds will download the matching release version automatically

### Groovy JAR API Usage

Based on the Groovy implementation structure, the JAR likely exposes:

```java
// Main entry point
com.cronquery.Main.main(String[] args)

// Core components (if exposed as library)
com.cronquery.QueryParser
com.cronquery.CronLoader
com.cronquery.ScheduleAnalyzer
```

**Integration Approach:**
1. Investigate JAR structure to identify public API classes
2. If library-style API exists, use direct class invocation
3. If only CLI interface exists, use programmatic argument passing to Main class
4. Capture output and parse results

**Fallback Strategy:**
If direct API access is limited, use process execution:
```java
ProcessBuilder pb = new ProcessBuilder("java", "-jar", "cron-query-groovy.jar", "jobs", "on", "weekends");
// Capture and parse output
```

However, direct class invocation is preferred for:
- Better performance (no process overhead)
- Easier error handling
- Type-safe integration
- Better testability

## Error Handling

### Exception Hierarchy

```
RuntimeException
├── CronQueryException (Base)
│   ├── InvalidQueryException → HTTP 400
│   ├── CrontabLoadException → HTTP 500
│   ├── GroovyJarException → HTTP 500
│   └── ConfigurationException → HTTP 500
```

### Error Response Strategy

1. **Client Errors (4xx)**
   - Invalid query syntax
   - Missing required parameters
   - Unsupported format
   - Return detailed error message with examples

2. **Server Errors (5xx)**
   - Crontab access failures
   - Groovy JAR invocation errors
   - Unexpected exceptions
   - Return generic message, log details

3. **Global Exception Handler**
   - `@ControllerAdvice` for centralized error handling
   - Consistent error response format
   - Request ID for tracing
   - Appropriate HTTP status codes

## Testing Strategy

### Unit Tests

**Controller Layer**
- Mock service layer
- Test request validation
- Test response formatting
- Test error handling

**Service Layer**
- Mock Groovy JAR adapter
- Test business logic
- Test exception translation
- Test response transformation

**Integration Layer**
- Mock Groovy JAR (if possible)
- Test crontab loading fallback logic
- Test configuration handling

### Integration Tests

**API Integration Tests**
- Use `@SpringBootTest` with `WebEnvironment.RANDOM_PORT`
- Test full request/response cycle
- Use test crontab file
- Verify JSON/CSV/YAML output formats

**Groovy JAR Integration Tests**
- Test actual Groovy JAR invocation
- Verify query translation
- Test error scenarios

### Test Data

Use existing `test_crontab.txt` from project root:
- Copy to `src/test/resources/test_crontab.txt`
- Configure test profile to use this file
- Ensure consistent test results across environments

## Docker Containerization

### Dockerfile Structure

**Multi-Stage Build:**

```dockerfile
# Stage 1: Build
FROM maven:3.9-openjdk-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY lib ./lib
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM openjdk:21-jre-slim
WORKDIR /app

# Copy JAR from build stage
COPY --from=builder /app/target/cron-query-service-*.jar app.jar

# Copy test crontab
COPY test_crontab.txt /app/test_crontab.txt

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose (Optional for testing)

```yaml
version: '3.8'
services:
  cron-query-service:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SERVER_PORT=8080
      - CRONTAB_TEST_FILE=/app/test_crontab.txt
    volumes:
      - ./test_crontab.txt:/app/test_crontab.txt:ro
```

## Deployment Considerations

### CentOS Deployment

**Prerequisites:**
- Java 21 JRE installed
- Port 8080 available (or configured alternative)
- Read access to crontab sources (if using system crontabs)

**Deployment Steps:**
1. Build JAR: `mvn clean package`
2. Copy JAR to CentOS server
3. Create systemd service file
4. Configure application.yml for production
5. Start service: `systemctl start cron-query-service`

**Systemd Service Example:**
```ini
[Unit]
Description=Cron Query Microservice
After=network.target

[Service]
Type=simple
User=cronquery
ExecStart=/usr/bin/java -jar /opt/cron-query-service/app.jar
Restart=on-failure
Environment="SERVER_PORT=8080"

[Install]
WantedBy=multi-user.target
```

### Docker Deployment

**Build Image:**
```bash
docker build -t cron-query-service:latest .
```

**Run Container:**
```bash
docker run -d \
  --name cron-query-service \
  -p 8080:8080 \
  -e SERVER_PORT=8080 \
  cron-query-service:latest
```

**With Volume Mount (for custom crontab):**
```bash
docker run -d \
  --name cron-query-service \
  -p 8080:8080 \
  -v /path/to/custom_crontab.txt:/app/test_crontab.txt:ro \
  -e CRONTAB_TEST_FILE=/app/test_crontab.txt \
  cron-query-service:latest
```

## Observability

### Logging

**Log Levels:**
- INFO: Request/response, startup, configuration
- WARN: Crontab source failures, fallback usage
- ERROR: Unexpected exceptions, critical failures

**Log Format:**
```
[timestamp] [level] [thread] [class] - message
```

**Key Log Points:**
- Service startup with active crontab sources
- Each API request with query parameters
- Groovy JAR invocation and execution time
- Crontab loading attempts and results
- Exception details with stack traces

### Metrics

**Custom Metrics:**
- `cronquery.requests.total`: Total API requests
- `cronquery.requests.duration`: Request duration histogram
- `cronquery.groovyjar.invocations`: Groovy JAR calls
- `cronquery.crontab.sources.active`: Active crontab sources gauge
- `cronquery.errors.total`: Error count by type

**Spring Boot Actuator Metrics:**
- JVM metrics (memory, threads, GC)
- HTTP metrics (request count, duration, status)
- System metrics (CPU, disk, network)

### Health Checks

**Health Indicators:**
1. **Application Health**: Service is running
2. **Crontab Health**: At least one crontab source accessible
3. **Groovy JAR Health**: Can invoke Groovy JAR successfully

**Health Response:**
```json
{
  "status": "UP",
  "components": {
    "crontabLoader": {
      "status": "UP",
      "details": {
        "sources": ["test_file"],
        "jobCount": 15
      }
    },
    "groovyJar": {
      "status": "UP",
      "details": {
        "version": "1.2.2"
      }
    }
  }
}
```

## Security Considerations

### Input Validation

- Sanitize all query parameters
- Limit query string length
- Validate day/time formats
- Prevent command injection in crontab parsing

### Access Control

- No authentication required for this learning project
- Consider adding API key authentication for production
- CORS configuration for web client access

### Crontab Access

- Read-only access to crontab files
- Handle permission errors gracefully
- Don't expose sensitive crontab content in errors
- Log access attempts for audit

## Performance Considerations

### Caching Strategy

**Crontab Caching:**
- Cache loaded crontab data for configurable TTL (e.g., 5 minutes)
- Invalidate on configuration change
- Reduces file I/O for repeated queries

**Query Result Caching:**
- Optional: Cache query results for identical queries
- Short TTL (1-2 minutes) to balance freshness and performance
- Use Spring Cache abstraction

### Optimization

- Lazy load Groovy JAR classes
- Reuse Groovy JAR instances (if stateless)
- Async logging for high-throughput scenarios
- Connection pooling (if database added later)

## Future Enhancements

### Phase 2 Features (Not in MVP)

1. **Ad-hoc Crontab Query (POST endpoint)**
   - Allow users to submit raw crontab content via POST request
   - Query against provided crontab without server-side file access
   - Enables web UI integration, CI/CD validation, and remote analysis
   
   **API Design:**
   ```
   POST /api/jobs/query
   Content-Type: application/json
   
   {
     "crontab": "0 8 * * 1-5 /usr/bin/backup.sh\n30 12 * * * /usr/bin/lunch.sh",
     "query": "jobs on weekdays",
     "format": "json"
   }
   ```
   
   **Benefits:**
   - Stateless operation (no file system dependency)
   - Users can analyze crontabs from any system
   - Web UI can provide paste-and-query functionality
   - CI/CD pipelines can validate crontab files before deployment

2. **Advanced Queries**
   - Next N execution times for specific job
   - Job conflict detection (overlapping schedules)
   - Schedule visualization endpoints

3. **Persistence**
   - Store query history
   - Cache crontab snapshots
   - Historical analysis

4. **Authentication**
   - API key authentication
   - OAuth2 integration
   - Role-based access control

5. **Multi-tenancy**
   - Support multiple crontab sources per tenant
   - Tenant-specific configurations

6. **WebSocket Support**
   - Real-time crontab change notifications
   - Live query results streaming

## Technology Stack Summary

- **Framework**: Spring Boot 3.2.x
- **Java Version**: Java 21
- **Build Tool**: Maven 3.9+
- **Groovy JAR**: cron-query-groovy-1.2.2.jar
- **API Documentation**: SpringDoc OpenAPI 3
- **Metrics**: Micrometer + Prometheus
- **Testing**: JUnit 5, Mockito, Spring Boot Test
- **Containerization**: Docker with multi-stage builds
- **Deployment**: CentOS 7+, Docker

## Project Structure

```
src/java/cron-query-service/
├── src/
│   ├── main/
│   │   ├── java/com/cronquery/service/
│   │   │   ├── CronQueryServiceApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── CronQueryController.java
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── service/
│   │   │   │   ├── CronQueryService.java
│   │   │   │   └── CronQueryServiceImpl.java
│   │   │   ├── integration/
│   │   │   │   ├── GroovyJarAdapter.java
│   │   │   │   ├── GroovyJarAdapterImpl.java
│   │   │   │   ├── CrontabLoader.java
│   │   │   │   └── CrontabLoaderImpl.java
│   │   │   ├── model/
│   │   │   │   ├── QueryRequest.java
│   │   │   │   ├── QueryResponse.java
│   │   │   │   ├── CronJob.java
│   │   │   │   ├── ErrorResponse.java
│   │   │   │   └── HealthStatus.java
│   │   │   ├── config/
│   │   │   │   ├── CronQueryConfig.java
│   │   │   │   └── OpenApiConfig.java
│   │   │   └── exception/
│   │   │       ├── CronQueryException.java
│   │   │       ├── InvalidQueryException.java
│   │   │       ├── CrontabLoadException.java
│   │   │       └── GroovyJarException.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── test_crontab.txt
│   └── test/
│       ├── java/com/cronquery/service/
│       │   ├── controller/
│       │   │   └── CronQueryControllerTest.java
│       │   ├── service/
│       │   │   └── CronQueryServiceTest.java
│       │   └── integration/
│       │       ├── GroovyJarAdapterTest.java
│       │       └── CrontabLoaderTest.java
│       └── resources/
│           └── test_crontab.txt
├── lib/
│   └── cron-query-groovy-1.2.2.jar
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```
