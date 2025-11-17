# Cron Query Service

Spring Boot microservice wrapper for the cron-query Groovy implementation, providing REST API access to cron schedule analysis.

## Overview

This microservice exposes the existing cron-query functionality through HTTP endpoints, enabling web applications and services to query and analyze cron schedules.

## Prerequisites

- Java 21 or higher
- Maven 3.9 or higher

## Building

The Maven build automatically downloads the Groovy JAR from GitHub releases:

```bash
mvn clean package
```

This will:
1. Download `cron-query-groovy-1.2.2.jar` from GitHub releases
2. Build the Spring Boot application
3. Create an executable JAR in `target/`

## Running

### Local Development

```bash
mvn spring-boot:run
```

Or run the built JAR:

```bash
java -jar target/cron-query-service-1.2.2.jar
```

### Configuration

Configure via environment variables or `application.yml`:

- `SERVER_PORT`: Server port (default: 8080)
- `CRONTAB_TEST_FILE`: Path to test crontab file (default: test_crontab.txt)

## API Documentation

Once running, access:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/api-docs
- **Health Check**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/prometheus

## Project Structure

```
src/java/cron-query-service/
├── src/
│   ├── main/
│   │   ├── java/com/cronquery/service/
│   │   │   └── CronQueryServiceApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/cronquery/service/
├── lib/                    # Downloaded Groovy JAR (auto-generated)
├── pom.xml
└── README.md
```

## Development

This is a learning project for:
- Spring Boot microservice architecture
- REST API design
- Docker containerization
- Integration with existing Groovy implementation

## License

See main project LICENSE file.
