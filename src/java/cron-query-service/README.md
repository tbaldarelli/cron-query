# Cron Query Service

Spring Boot microservice wrapper for the cron-query Groovy implementation, providing REST API access to cron schedule analysis.

## Overview

This microservice exposes the existing cron-query functionality through HTTP endpoints, enabling web applications and services to query and analyze cron schedules using natural language or structured parameters.

### Key Features

- **REST API**: Query cron schedules via HTTP endpoints
- **Natural Language Queries**: Support for queries like "jobs on weekends" or "what runs at 8 AM"
- **Multiple Output Formats**: JSON, CSV, and YAML response formats
- **Multiple Crontab Sources**: User crontab, system crontab, and cron.d directories
- **Production Ready**: Health checks, metrics, and OpenAPI documentation
- **Docker Support**: Containerized deployment with test data
- **Test Data Fallback**: Works on Windows/non-Linux systems with test crontab file

### Purpose

This is a learning project demonstrating:
- Spring Boot microservice architecture
- REST API design and documentation
- Docker containerization
- Integration with existing Groovy JAR libraries
- Observability (health checks, metrics, logging)
- Configuration management and profiles

## Prerequisites

- **Java 21** or higher
- **Maven 3.9** or higher
- **Docker** (optional, for containerized deployment)

## Quick Start

### 1. Build the Application

The Maven build automatically downloads the Groovy JAR from GitHub releases:

```bash
mvn clean package
```

This will:
1. Download `cron-query-groovy-1.2.2.jar` from GitHub releases
2. Build the Spring Boot application
3. Create an executable JAR in `target/`

### 2. Run the Application

```bash
java -jar target/cron-query-service-1.2.2.jar
```

Or use Maven:

```bash
mvn spring-boot:run
```

### 3. Access the API

Open your browser to:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health

## API Endpoints

### Query Cron Jobs

**Endpoint**: `GET /api/jobs`

Query cron schedules using natural language or structured parameters.

#### Query Parameters

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `query` | String | Natural language query | `jobs on weekends` |
| `day` | String | Day name or date | `Monday`, `2024-12-25` |
| `time` | String | Time in HH:MM format | `08:00` |
| `timeRange` | String | Time range | `08:00-17:00` |
| `format` | String | Output format (json, csv, yaml) | `json` (default) |

#### Example Requests

**Natural Language Query:**
```bash
curl "http://localhost:8080/api/jobs?query=jobs%20on%20weekends"
```

**Structured Query (Day and Time):**
```bash
curl "http://localhost:8080/api/jobs?day=Monday&time=08:00"
```

**Time Range Query:**
```bash
curl "http://localhost:8080/api/jobs?timeRange=08:00-17:00"
```

**CSV Output:**
```bash
curl "http://localhost:8080/api/jobs?query=jobs%20on%20Saturday&format=csv"
```

#### Example Response (JSON)

```json
{
  "jobs": [
    {
      "schedule": "0 8 * * 6",
      "command": "/usr/bin/backup.sh",
      "source": "test_crontab.txt",
      "user": null,
      "nextRuns": [
        "2024-12-21 08:00:00",
        "2024-12-28 08:00:00"
      ]
    }
  ],
  "totalCount": 1,
  "query": "jobs on Saturday",
  "sources": ["test_crontab.txt"],
  "executionTimeMs": 45
}
```

#### Error Responses

**400 Bad Request** - Invalid query parameters:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid time format. Expected HH:MM",
  "path": "/api/jobs",
  "timestamp": 1703174400000
}
```

**500 Internal Server Error** - Server-side error:
```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "Failed to process crontab data",
  "path": "/api/jobs",
  "timestamp": 1703174400000
}
```

### Health Check

**Endpoint**: `GET /actuator/health`

Returns service health status including crontab loader and Groovy JAR integration status.

**Example Response:**
```json
{
  "status": "UP",
  "components": {
    "crontabLoader": {
      "status": "UP",
      "details": {
        "sources": ["test_crontab.txt"],
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

### Metrics

**Endpoint**: `GET /actuator/prometheus`

Prometheus-compatible metrics for monitoring.

### API Documentation

**Endpoints**:
- **Swagger UI**: http://localhost:8080/swagger-ui.html (Interactive API documentation)
- **OpenAPI Spec**: http://localhost:8080/api-docs (JSON specification)

## Configuration Options

The service can be configured via environment variables or `application.yml` files.

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP server port | `8080` |
| `CRONTAB_TEST_FILE` | Path to test crontab file | `test_crontab.txt` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (dev, prod) | None |
| `LOGGING_LEVEL_ROOT` | Root logging level | `INFO` |
| `CORS_ALLOWED_ORIGINS` | CORS allowed origins | `*` |

### Configuration Files

**application.yml** (Base configuration):
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

spring:
  application:
    name: cron-query-service

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

**application-dev.yml** (Development profile):
- Uses test crontab file
- Verbose logging
- CORS enabled for local development

**application-prod.yml** (Production profile):
- Uses system crontab sources
- Production logging levels
- Restricted CORS

### Using Profiles

**Development:**
```bash
java -jar target/cron-query-service-1.2.2.jar --spring.profiles.active=dev
```

**Production:**
```bash
java -jar target/cron-query-service-1.2.2.jar --spring.profiles.active=prod
```

## Test Data Approach

The service includes a test crontab file for development and testing on systems without real crontab access (e.g., Windows).

### Test Crontab File

Located at `src/main/resources/test_crontab.txt`, this file contains sample cron jobs for testing:

```
# Sample cron jobs for testing
0 8 * * 1-5 /usr/bin/backup.sh
30 12 * * * /usr/bin/lunch-reminder.sh
0 0 * * 6 /usr/bin/weekly-report.sh
```

### Fallback Behavior

The service attempts to load crontab data in this order:
1. User crontab (`crontab -l`)
2. System crontab (`/etc/crontab`)
3. Cron directories (`/etc/cron.d/*`)
4. Test file (fallback)

On Windows or systems without crontab access, the service automatically falls back to the test file, allowing full functionality for development and testing.

### Custom Test Data

You can provide your own test crontab file:

```bash
java -jar target/cron-query-service-1.2.2.jar --CRONTAB_TEST_FILE=/path/to/custom_crontab.txt
```

Or via environment variable:
```bash
export CRONTAB_TEST_FILE=/path/to/custom_crontab.txt
java -jar target/cron-query-service-1.2.2.jar
```

## Docker Deployment

### Building the Docker Image

```bash
docker build -t cron-query-service:latest .
```

The Dockerfile uses a multi-stage build:
1. **Build stage**: Downloads Groovy JAR and builds the application
2. **Runtime stage**: Creates minimal image with JRE and application

### Running the Container

**Basic run:**
```bash
docker run -d \
  --name cron-query-service \
  -p 8080:8080 \
  cron-query-service:latest
```

**With custom port:**
```bash
docker run -d \
  --name cron-query-service \
  -p 9090:9090 \
  -e SERVER_PORT=9090 \
  cron-query-service:latest
```

**With custom crontab file:**
```bash
docker run -d \
  --name cron-query-service \
  -p 8080:8080 \
  -v /path/to/custom_crontab.txt:/app/test_crontab.txt:ro \
  -e CRONTAB_TEST_FILE=/app/test_crontab.txt \
  cron-query-service:latest
```

### Using Docker Compose

Create a `docker-compose.yml`:

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
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 3
```

Run with:
```bash
docker-compose up -d
```

### Accessing the Containerized Service

Once running, access the service at:
- **API**: http://localhost:8080/api/jobs
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health

### Docker Health Checks

The container includes a health check that verifies the service is responding:

```bash
docker ps  # Check health status in STATUS column
```

## CentOS Deployment

### Prerequisites

Install Java 21 on CentOS:

```bash
# Install Java 21
sudo yum install java-21-openjdk java-21-openjdk-devel

# Verify installation
java -version
```

### Deployment Steps

#### 1. Build the Application

On your development machine:
```bash
mvn clean package
```

#### 2. Copy to CentOS Server

```bash
scp target/cron-query-service-1.2.2.jar user@centos-server:/opt/cron-query-service/
scp src/main/resources/test_crontab.txt user@centos-server:/opt/cron-query-service/
```

#### 3. Create Application User

```bash
sudo useradd -r -s /bin/false cronquery
sudo mkdir -p /opt/cron-query-service
sudo chown cronquery:cronquery /opt/cron-query-service
```

#### 4. Create Systemd Service

Create `/etc/systemd/system/cron-query-service.service`:

```ini
[Unit]
Description=Cron Query Microservice
After=network.target

[Service]
Type=simple
User=cronquery
Group=cronquery
WorkingDirectory=/opt/cron-query-service
ExecStart=/usr/bin/java -jar /opt/cron-query-service/cron-query-service-1.2.2.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=cron-query-service

# Environment variables
Environment="SERVER_PORT=8080"
Environment="SPRING_PROFILES_ACTIVE=prod"

[Install]
WantedBy=multi-user.target
```

#### 5. Enable and Start Service

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service to start on boot
sudo systemctl enable cron-query-service

# Start service
sudo systemctl start cron-query-service

# Check status
sudo systemctl status cron-query-service
```

#### 6. Verify Deployment

```bash
# Check if service is running
curl http://localhost:8080/actuator/health

# Test API
curl "http://localhost:8080/api/jobs?query=jobs%20on%20Monday"
```

### Viewing Logs

```bash
# Follow logs in real-time
sudo journalctl -u cron-query-service -f

# View recent logs
sudo journalctl -u cron-query-service -n 100

# View logs since boot
sudo journalctl -u cron-query-service -b
```

### Firewall Configuration

If using firewalld, open the port:

```bash
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

### Updating the Service

```bash
# Stop service
sudo systemctl stop cron-query-service

# Replace JAR file
sudo cp new-version.jar /opt/cron-query-service/cron-query-service-1.2.2.jar
sudo chown cronquery:cronquery /opt/cron-query-service/cron-query-service-1.2.2.jar

# Start service
sudo systemctl start cron-query-service
```

## Build and Run Instructions

### Local Development Build

```bash
# Clean and build
mvn clean package

# Run with development profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or run the JAR directly
java -jar target/cron-query-service-1.2.2.jar --spring.profiles.active=dev
```

### Production Build

```bash
# Build with production optimizations
mvn clean package -Pprod

# Run with production profile
java -jar target/cron-query-service-1.2.2.jar --spring.profiles.active=prod
```

### Running Tests

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# Run specific test class
mvn test -Dtest=CronQueryControllerTest
```

### Skipping Tests

```bash
# Build without running tests
mvn clean package -DskipTests
```

## Project Structure

```
src/java/cron-query-service/
├── src/
│   ├── main/
│   │   ├── java/com/cronquery/service/
│   │   │   ├── CronQueryServiceApplication.java    # Main application class
│   │   │   ├── controller/
│   │   │   │   ├── CronQueryController.java        # REST API endpoints
│   │   │   │   └── GlobalExceptionHandler.java     # Error handling
│   │   │   ├── service/
│   │   │   │   ├── CronQueryService.java           # Service interface
│   │   │   │   └── CronQueryServiceImpl.java       # Business logic
│   │   │   ├── integration/
│   │   │   │   ├── GroovyJarAdapter.java           # Groovy JAR interface
│   │   │   │   ├── GroovyJarAdapterImpl.java       # Groovy integration
│   │   │   │   ├── CrontabLoader.java              # Crontab loader interface
│   │   │   │   └── CrontabLoaderImpl.java          # Multi-source loading
│   │   │   ├── model/
│   │   │   │   ├── QueryRequest.java               # Request DTO
│   │   │   │   ├── QueryResponse.java              # Response DTO
│   │   │   │   ├── CronJob.java                    # Job model
│   │   │   │   ├── ErrorResponse.java              # Error DTO
│   │   │   │   └── HealthStatus.java               # Health model
│   │   │   ├── config/
│   │   │   │   ├── CorsConfig.java                 # CORS configuration
│   │   │   │   ├── CrontabSourceConfig.java        # Crontab config
│   │   │   │   ├── MetricsConfig.java              # Metrics config
│   │   │   │   └── OpenApiConfig.java              # API docs config
│   │   │   ├── health/
│   │   │   │   ├── CrontabLoaderHealthIndicator.java
│   │   │   │   └── GroovyJarHealthIndicator.java
│   │   │   └── exception/
│   │   │       ├── CronQueryException.java         # Base exception
│   │   │       ├── InvalidQueryException.java      # Client errors
│   │   │       ├── CrontabLoadException.java       # Load errors
│   │   │       └── GroovyJarException.java         # Integration errors
│   │   └── resources/
│   │       ├── application.yml                     # Base config
│   │       ├── application-dev.yml                 # Dev config
│   │       ├── application-prod.yml                # Prod config
│   │       └── test_crontab.txt                    # Test data
│   └── test/
│       ├── java/com/cronquery/service/             # Test classes
│       └── resources/
│           └── test_crontab.txt                    # Test data
├── lib/
│   └── cron-query-groovy-1.2.2.jar                # Auto-downloaded
├── Dockerfile                                       # Container definition
├── docker-compose.yml                              # Docker Compose config
├── pom.xml                                         # Maven configuration
└── README.md                                       # This file
```

## Troubleshooting

### Common Issues

**Issue**: Maven fails to download Groovy JAR
```
Solution: Check GitHub releases exist for the version specified in pom.xml
Verify: https://github.com/tbaldarelli/cron-query/releases
```

**Issue**: Service fails to start with "Address already in use"
```
Solution: Change the port using SERVER_PORT environment variable
Example: SERVER_PORT=9090 java -jar target/cron-query-service-1.2.2.jar
```

**Issue**: No crontab data found
```
Solution: Verify test_crontab.txt exists in the working directory
Or set: CRONTAB_TEST_FILE=/path/to/test_crontab.txt
```

**Issue**: Health check shows DOWN status
```
Solution: Check logs for specific component failures
Command: docker logs cron-query-service (for Docker)
Command: sudo journalctl -u cron-query-service (for systemd)
```

### Logging

Enable debug logging:
```bash
java -jar target/cron-query-service-1.2.2.jar --logging.level.com.cronquery=DEBUG
```

Or via environment variable:
```bash
export LOGGING_LEVEL_COM_CRONQUERY=DEBUG
java -jar target/cron-query-service-1.2.2.jar
```

## Contributing

This is a learning project. Contributions and suggestions are welcome!

## License

See main project LICENSE file.

## Related Documentation

- [Main Project README](../../../README.md)
- [Groovy Implementation](../../../src/groovy/)
- [Python Implementation](../../../src/python/)
- [OpenAPI Documentation](OPENAPI_DOCUMENTATION.md)
- [Docker Guide](DOCKER.md)
- [Groovy JAR Integration](GROOVY_JAR_INTEGRATION.md)
