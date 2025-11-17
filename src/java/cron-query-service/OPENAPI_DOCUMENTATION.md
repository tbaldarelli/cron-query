# OpenAPI/Swagger Documentation

## Overview

The Cron Query Service provides comprehensive OpenAPI 3.0 documentation for all REST endpoints, making it easy for developers to understand and integrate with the API.

## Accessing the Documentation

Once the service is running, you can access the documentation at:

- **Swagger UI (Interactive)**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON Spec**: http://localhost:8080/api-docs
- **OpenAPI YAML Spec**: http://localhost:8080/api-docs.yaml

## Configuration

### OpenAPI Configuration Class

The `OpenApiConfig` class (`com.cronquery.service.config.OpenApiConfig`) provides:

- **API Metadata**: Title, description, version
- **Contact Information**: Project URL and support email
- **License Information**: MIT License
- **Server Configuration**: Local and production server URLs
- **External Documentation**: Links to GitHub repository

### Application Configuration

OpenAPI settings in `application.yml`:

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

## Documented Endpoints

### GET /api/jobs

Query cron jobs using natural language or structured parameters.

**Parameters:**
- `query` (optional): Natural language query (e.g., "jobs on weekends")
- `day` (optional): Day filter (e.g., "Monday")
- `time` (optional): Time filter in HH:MM format (e.g., "08:00")
- `timeRange` (optional): Time range in HH:MM-HH:MM format (e.g., "08:00-17:00")
- `format` (optional): Output format - json, csv, or yaml (default: json)

**Response Codes:**
- `200 OK`: Successfully retrieved matching cron jobs
- `400 Bad Request`: Invalid query parameters
- `500 Internal Server Error`: Server error during query processing

**Example Responses:**

Success (200):
```json
{
  "jobs": [
    {
      "schedule": "0 8 * * 6,0",
      "command": "/usr/bin/backup.sh",
      "source": "/etc/crontab",
      "user": "root",
      "nextRuns": ["2024-11-16 08:00:00", "2024-11-17 08:00:00"],
      "description": "At 08:00 on Saturday and Sunday"
    }
  ],
  "totalCount": 1,
  "query": "jobs on weekends",
  "sources": ["/etc/crontab"],
  "executionTimeMs": 45
}
```

Error (400):
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Time must be in HH:MM format",
  "path": "/api/jobs",
  "timestamp": 1699876543210
}
```

## Data Models

All data models are fully documented with OpenAPI Schema annotations:

### QueryRequest
- Natural language and structured query parameters
- Validation patterns for time formats
- Output format selection

### QueryResponse
- List of matching cron jobs
- Query metadata (execution time, sources)
- Total count of results

### CronJob
- Cron schedule expression
- Command to execute
- Source file location
- User ownership
- Next execution times
- Human-readable description

### ErrorResponse
- HTTP status code
- Error type and message
- Request path
- Timestamp

### OutputFormat (Enum)
- JSON (default)
- CSV
- YAML

## Features

### Interactive API Testing

The Swagger UI provides:
- **Try It Out**: Test API endpoints directly from the browser
- **Request Examples**: Pre-filled example requests
- **Response Examples**: Sample responses for each status code
- **Schema Validation**: Real-time validation of request parameters

### Code Generation

The OpenAPI specification can be used to generate client libraries in various languages:

```bash
# Download the OpenAPI spec
curl http://localhost:8080/api-docs > openapi.json

# Generate client using OpenAPI Generator
openapi-generator-cli generate -i openapi.json -g java -o ./client
```

### API Versioning

The API version is automatically synchronized with the project version from `pom.xml`:
- Current version: 1.2.2
- Version displayed in Swagger UI and OpenAPI spec

## Query Examples in Documentation

The documentation includes comprehensive query examples:

1. **Natural Language Queries**:
   - "jobs on Saturday"
   - "what runs at 8 AM"
   - "weekend jobs"

2. **Structured Queries**:
   - `?day=Monday&time=09:00`
   - `?timeRange=08:00-17:00`

3. **Format Options**:
   - `?query=jobs on Saturday&format=json`
   - `?query=jobs on Saturday&format=csv`
   - `?query=jobs on Saturday&format=yaml`

## Integration with Actuator

The OpenAPI documentation complements Spring Boot Actuator endpoints:

- **Health**: http://localhost:8080/actuator/health
- **Info**: http://localhost:8080/actuator/info
- **Metrics**: http://localhost:8080/actuator/prometheus

## Best Practices

### For API Consumers

1. **Start with Swagger UI**: Use the interactive documentation to understand the API
2. **Check Examples**: Review the example requests and responses
3. **Validate Parameters**: Use the schema information to validate your requests
4. **Handle Errors**: Implement proper error handling for 400 and 500 responses

### For API Developers

1. **Keep Documentation Updated**: Update OpenAPI annotations when changing endpoints
2. **Add Examples**: Include realistic examples for all request/response scenarios
3. **Document Errors**: Clearly document all possible error conditions
4. **Version Carefully**: Maintain backward compatibility or version the API

## Troubleshooting

### Swagger UI Not Loading

1. Check that the service is running: `curl http://localhost:8080/actuator/health`
2. Verify the Swagger UI path: http://localhost:8080/swagger-ui.html
3. Check application logs for SpringDoc initialization errors

### OpenAPI Spec Not Available

1. Verify the api-docs path in `application.yml`
2. Check that SpringDoc dependency is in `pom.xml`
3. Ensure the service started without errors

### Missing Documentation

1. Verify `@Operation` annotations on controller methods
2. Check `@Schema` annotations on model classes
3. Ensure `OpenApiConfig` bean is being loaded

## References

- [SpringDoc OpenAPI Documentation](https://springdoc.org/)
- [OpenAPI Specification](https://swagger.io/specification/)
- [Swagger UI](https://swagger.io/tools/swagger-ui/)
