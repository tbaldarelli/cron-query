# Docker Validation Checklist

This checklist ensures all Docker-related requirements are met and the container is production-ready.

## Pre-Build Validation

- [ ] Docker is installed and running
- [ ] Docker version is 20.10 or higher
- [ ] Docker Compose is installed (if using compose)
- [ ] Port 8080 is available on host machine
- [ ] At least 2GB free disk space available
- [ ] test_crontab.txt file exists in project root

## Build Validation

### Image Build (Requirement 4.1)

- [ ] `docker build` command completes without errors
- [ ] Maven downloads Groovy JAR from GitHub releases successfully
- [ ] Application compiles without errors
- [ ] JAR file is created in target directory
- [ ] Multi-stage build completes both stages
- [ ] Final image is created successfully

**Validation Command:**
```bash
docker build -t cron-query-service:latest .
docker images | grep cron-query-service
```

**Expected Result:**
- Build completes with "Successfully built" message
- Image appears in `docker images` list
- Image size is approximately 400-500MB

### Multi-Stage Build (Requirement 4.4)

- [ ] Builder stage uses Maven and JDK 21
- [ ] Runtime stage uses JRE 21 (smaller footprint)
- [ ] Only necessary files copied to runtime stage
- [ ] Image size is optimized (< 600MB)

**Validation Command:**
```bash
docker history cron-query-service:latest
```

**Expected Result:**
- Two distinct stages visible in history
- Runtime stage is significantly smaller than builder stage

## Container Startup Validation

### Basic Startup

- [ ] Container starts without errors
- [ ] Application logs show successful startup
- [ ] "Started CronQueryServiceApplication" message appears in logs
- [ ] No error or exception messages in startup logs
- [ ] Container reaches "running" state

**Validation Commands:**
```bash
docker run -d --name cron-query-service -p 8080:8080 cron-query-service:latest
docker ps | grep cron-query-service
docker logs cron-query-service
```

**Expected Result:**
- Container ID returned
- Container shows as "Up" in `docker ps`
- Logs show successful Spring Boot startup

### Port Configuration (Requirement 4.3)

- [ ] Container exposes port 8080
- [ ] Port mapping works correctly (host:container)
- [ ] Service is accessible on mapped port
- [ ] Environment variable SERVER_PORT can override default

**Validation Commands:**
```bash
docker port cron-query-service
curl http://localhost:8080/actuator/health
```

**Expected Result:**
- Port mapping shows 8080/tcp -> 0.0.0.0:8080
- Health check returns HTTP 200

### Test Crontab File (Requirement 4.2)

- [ ] Test crontab file is included in image
- [ ] Fallback crontab is created if volume not mounted
- [ ] File is readable by application
- [ ] File contains valid cron entries

**Validation Commands:**
```bash
docker exec cron-query-service cat /app/test_crontab.txt
docker exec cron-query-service wc -l /app/test_crontab.txt
```

**Expected Result:**
- File exists and is readable
- Contains multiple cron job entries
- No permission errors

## Health Check Validation (Requirement 4.5)

### Docker Health Check

- [ ] HEALTHCHECK instruction is in Dockerfile
- [ ] Health check interval is 30 seconds
- [ ] Health check timeout is 3 seconds
- [ ] Start period is 40 seconds
- [ ] Health check uses curl to test /actuator/health
- [ ] Container reaches "healthy" status

**Validation Commands:**
```bash
docker inspect cron-query-service | grep -A 10 Health
docker ps --format "table {{.Names}}\t{{.Status}}"
```

**Expected Result:**
- Health status shows "healthy" after ~40 seconds
- Health check configuration matches Dockerfile

### Application Health Endpoint

- [ ] /actuator/health endpoint returns HTTP 200
- [ ] Response contains "status": "UP"
- [ ] CrontabLoader health indicator is present
- [ ] GroovyJar health indicator is present
- [ ] Health details include source information
- [ ] Health details include job count

**Validation Command:**
```bash
curl http://localhost:8080/actuator/health | jq '.'
```

**Expected Result:**
```json
{
  "status": "UP",
  "components": {
    "crontabLoader": {
      "status": "UP",
      "details": {
        "sources": ["test_file"],
        "jobCount": 85
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

## API Functionality Validation

### Natural Language Queries

- [ ] Query with "jobs on weekdays" returns results
- [ ] Query with "jobs on weekends" returns results
- [ ] Query with "jobs at 8 AM" returns results
- [ ] Query with time ranges works correctly
- [ ] Invalid queries return appropriate error messages

**Validation Commands:**
```bash
curl "http://localhost:8080/api/jobs?query=jobs+on+weekdays"
curl "http://localhost:8080/api/jobs?query=jobs+on+weekends"
curl "http://localhost:8080/api/jobs?query=jobs+at+8+AM"
```

**Expected Result:**
- Each query returns valid JSON response
- Response includes "jobs" array and "totalCount"
- Job count is appropriate for query

### Structured Queries

- [ ] Query by day parameter works
- [ ] Query by time parameter works
- [ ] Query by timeRange parameter works
- [ ] Combined parameters work correctly

**Validation Commands:**
```bash
curl "http://localhost:8080/api/jobs?day=Monday"
curl "http://localhost:8080/api/jobs?time=07:00"
curl "http://localhost:8080/api/jobs?timeRange=08:00-17:00"
curl "http://localhost:8080/api/jobs?day=Monday&timeRange=08:00-17:00"
```

**Expected Result:**
- All queries return valid responses
- Results match query criteria

### Output Formats

- [ ] JSON format (default) works
- [ ] CSV format works
- [ ] YAML format works
- [ ] Format parameter is case-insensitive

**Validation Commands:**
```bash
curl "http://localhost:8080/api/jobs?query=all+jobs&format=json"
curl "http://localhost:8080/api/jobs?query=all+jobs&format=csv"
curl "http://localhost:8080/api/jobs?query=all+jobs&format=yaml"
```

**Expected Result:**
- JSON: Valid JSON with proper structure
- CSV: Headers and comma-separated values
- YAML: Valid YAML with proper indentation

## Volume Mount Validation

### Custom Crontab File

- [ ] Container accepts volume-mounted crontab file
- [ ] Mounted file overrides default test file
- [ ] Application reads from mounted file
- [ ] Jobs from mounted file are queryable
- [ ] File permissions are correct (read-only)

**Validation Commands:**
```bash
# Create custom crontab
echo "0 9 * * 1-5 /usr/bin/test.sh" > custom_crontab.txt

# Run with volume mount
docker run -d --name cron-query-custom \
  -p 8081:8080 \
  -v "$(pwd)/custom_crontab.txt:/app/test_crontab.txt:ro" \
  -e CRONTAB_TEST_FILE=/app/test_crontab.txt \
  cron-query-service:latest

# Test
curl "http://localhost:8081/api/jobs?query=all+jobs"
```

**Expected Result:**
- Container starts successfully
- API returns jobs from custom crontab file
- Job count matches custom file

## Documentation Validation

### Swagger UI

- [ ] Swagger UI is accessible at /swagger-ui.html
- [ ] All endpoints are documented
- [ ] Request parameters are documented
- [ ] Response schemas are documented
- [ ] Example requests/responses are provided
- [ ] "Try it out" functionality works

**Validation Command:**
```bash
curl -s http://localhost:8080/swagger-ui.html | grep -o "<title>.*</title>"
```

**Expected Result:**
- Page loads successfully
- Title contains "Swagger UI"

### OpenAPI Specification

- [ ] OpenAPI spec is available at /api-docs
- [ ] Spec is valid JSON
- [ ] All endpoints are documented
- [ ] Schemas are defined

**Validation Command:**
```bash
curl http://localhost:8080/api-docs | jq '.info'
```

**Expected Result:**
- Valid JSON returned
- Contains API title, version, description

## Observability Validation

### Prometheus Metrics

- [ ] Metrics endpoint is accessible
- [ ] JVM metrics are present
- [ ] HTTP metrics are present
- [ ] Custom application metrics are present
- [ ] Metrics format is Prometheus-compatible

**Validation Command:**
```bash
curl http://localhost:8080/actuator/prometheus | grep "jvm_memory_used_bytes"
```

**Expected Result:**
- Metrics in Prometheus format
- Multiple metric types present

### Logging

- [ ] Application logs are visible via `docker logs`
- [ ] Log level is appropriate (INFO by default)
- [ ] No excessive ERROR or WARN messages
- [ ] Startup logs show configuration details
- [ ] Request logs show query parameters

**Validation Command:**
```bash
docker logs cron-query-service | tail -50
```

**Expected Result:**
- Logs are readable and well-formatted
- No unexpected errors

## Performance Validation

### Resource Usage

- [ ] Memory usage is reasonable (< 500MB)
- [ ] CPU usage is low at idle (< 5%)
- [ ] Container responds quickly to requests
- [ ] No memory leaks over time

**Validation Command:**
```bash
docker stats cron-query-service --no-stream
```

**Expected Result:**
- Memory: 200-400MB
- CPU: < 5% at idle

### Response Times

- [ ] Health check responds in < 100ms
- [ ] Simple queries respond in < 500ms
- [ ] Complex queries respond in < 2s
- [ ] Concurrent requests are handled correctly

**Validation Command:**
```bash
time curl -s http://localhost:8080/actuator/health > /dev/null
time curl -s "http://localhost:8080/api/jobs?query=all+jobs" > /dev/null
```

**Expected Result:**
- Health check: < 0.1s
- API query: < 1s

## Docker Compose Validation

### Compose File

- [ ] docker-compose.yml is valid
- [ ] Service definition is correct
- [ ] Port mapping is configured
- [ ] Environment variables are set
- [ ] Volume mounts are configured
- [ ] Health check is defined

**Validation Command:**
```bash
docker-compose config
```

**Expected Result:**
- No validation errors
- Configuration is displayed correctly

### Compose Operations

- [ ] `docker-compose up` starts service
- [ ] `docker-compose ps` shows running service
- [ ] `docker-compose logs` shows application logs
- [ ] `docker-compose down` stops and removes service
- [ ] Service restarts correctly after down/up

**Validation Commands:**
```bash
docker-compose up -d
docker-compose ps
docker-compose logs
docker-compose down
```

**Expected Result:**
- All commands execute successfully
- Service starts and stops cleanly

## Security Validation

### Non-Root User

- [ ] Container runs as non-root user
- [ ] User "cronquery" is created
- [ ] Application files are owned by cronquery user
- [ ] No unnecessary privileges

**Validation Command:**
```bash
docker exec cron-query-service whoami
docker exec cron-query-service id
```

**Expected Result:**
- User is "cronquery", not "root"
- UID is not 0

### File Permissions

- [ ] Application JAR has appropriate permissions
- [ ] Test crontab file is read-only
- [ ] No world-writable files

**Validation Command:**
```bash
docker exec cron-query-service ls -la /app/
```

**Expected Result:**
- Files owned by cronquery:cronquery
- Reasonable permissions (644 for files, 755 for directories)

## Error Handling Validation

### Invalid Queries

- [ ] Invalid query returns HTTP 400
- [ ] Error response includes helpful message
- [ ] Error response is properly formatted JSON

**Validation Command:**
```bash
curl -i "http://localhost:8080/api/jobs?query="
```

**Expected Result:**
- HTTP 400 Bad Request
- JSON error response with details

### Missing Crontab

- [ ] Service handles missing crontab gracefully
- [ ] Falls back to default test file
- [ ] Logs warning about missing file
- [ ] Service remains operational

**Validation:**
- Run container without volume mount
- Check health endpoint
- Verify fallback crontab is used

## Cleanup Validation

### Container Cleanup

- [ ] Container stops cleanly
- [ ] Container can be removed
- [ ] No orphaned processes
- [ ] Ports are released

**Validation Commands:**
```bash
docker stop cron-query-service
docker rm cron-query-service
docker ps -a | grep cron-query-service
```

**Expected Result:**
- Container stops without errors
- Container is removed
- No container remains in `docker ps -a`

### Image Cleanup

- [ ] Image can be removed
- [ ] No dangling images remain
- [ ] Build cache can be cleared

**Validation Commands:**
```bash
docker rmi cron-query-service:latest
docker images | grep cron-query-service
docker system prune -f
```

**Expected Result:**
- Image is removed successfully
- No cron-query-service images remain

## Requirements Mapping

This checklist validates the following requirements:

- **Requirement 4.1**: ✓ Dockerfile builds runnable container image
- **Requirement 4.2**: ✓ Docker image includes test crontab file
- **Requirement 4.3**: ✓ Service exposes on configurable port (8080)
- **Requirement 4.4**: ✓ Multi-stage build minimizes image size
- **Requirement 4.5**: ✓ Health check configuration included

## Sign-Off

- [ ] All checklist items completed
- [ ] All requirements validated
- [ ] Documentation reviewed
- [ ] Ready for production deployment

**Validated By:** ___________________  
**Date:** ___________________  
**Notes:** ___________________
