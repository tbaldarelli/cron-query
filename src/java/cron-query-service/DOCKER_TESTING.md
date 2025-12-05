# Docker Build and Testing Guide

This document provides comprehensive instructions for building and testing the cron-query-service Docker image.

## Prerequisites

- Docker Desktop installed (Windows/Mac) or Docker Engine (Linux)
- Docker Compose (usually included with Docker Desktop)
- At least 2GB of free disk space for the image
- Port 8080 available on the host machine

## Build Instructions

### 1. Build the Docker Image

Navigate to the cron-query-service directory and build the image:

```bash
cd src/java/cron-query-service
docker build -t cron-query-service:latest .
```

**Expected Output:**
- Maven will download dependencies
- The Groovy JAR will be downloaded from GitHub releases
- Application will be compiled and packaged
- Multi-stage build will create a minimal runtime image
- Final image size should be approximately 400-500MB

**Verify the build:**
```bash
docker images | grep cron-query-service
```

### 2. Run the Container (Standalone)

Run the container with default settings:

```bash
docker run -d \
  --name cron-query-service \
  -p 8080:8080 \
  cron-query-service:latest
```

**With custom test crontab file:**
```bash
docker run -d \
  --name cron-query-service \
  -p 8080:8080 \
  -v "$(pwd)/../../../test_crontab.txt:/app/test_crontab.txt:ro" \
  -e CRONTAB_TEST_FILE=/app/test_crontab.txt \
  cron-query-service:latest
```

### 3. Run with Docker Compose

Using Docker Compose (recommended for testing):

```bash
docker-compose up -d
```

**View logs:**
```bash
docker-compose logs -f
```

**Stop the service:**
```bash
docker-compose down
```

## Testing the Container

### 1. Wait for Startup

The application takes approximately 30-40 seconds to start. Monitor the logs:

```bash
docker logs -f cron-query-service
```

Look for the message:
```
Started CronQueryServiceApplication in X.XXX seconds
```

### 2. Test Health Check Endpoint

Verify the service is healthy:

```bash
curl http://localhost:8080/actuator/health
```

**Expected Response:**
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

### 3. Test API Endpoints

**Query jobs on weekdays:**
```bash
curl "http://localhost:8080/api/jobs?query=jobs+on+weekdays"
```

**Query jobs at specific time:**
```bash
curl "http://localhost:8080/api/jobs?time=07:00"
```

**Query with structured parameters:**
```bash
curl "http://localhost:8080/api/jobs?day=Monday&timeRange=08:00-17:00"
```

**Get CSV format:**
```bash
curl "http://localhost:8080/api/jobs?query=jobs+on+weekends&format=csv"
```

**Get YAML format:**
```bash
curl "http://localhost:8080/api/jobs?query=all+jobs&format=yaml"
```

### 4. Test Swagger UI

Open in browser:
```
http://localhost:8080/swagger-ui.html
```

You should see the interactive API documentation with all endpoints.

### 5. Test Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

Should return Prometheus-formatted metrics including:
- JVM metrics
- HTTP request metrics
- Custom application metrics

### 6. Test with Volume-Mounted Crontab

Create a custom test crontab file:

```bash
cat > custom_crontab.txt << 'EOF'
# Custom test crontab
0 8 * * 1-5 /usr/bin/morning-backup.sh
30 12 * * * /usr/bin/lunch-reminder.sh
0 0 * * 0 /usr/bin/weekly-report.sh
EOF
```

Run container with custom crontab:

```bash
docker run -d \
  --name cron-query-service-custom \
  -p 8081:8080 \
  -v "$(pwd)/custom_crontab.txt:/app/test_crontab.txt:ro" \
  -e CRONTAB_TEST_FILE=/app/test_crontab.txt \
  cron-query-service:latest
```

Test the custom data:

```bash
curl "http://localhost:8081/api/jobs?query=all+jobs"
```

Should return only the 3 jobs from the custom crontab.

### 7. Verify Health Check in Container

Docker's built-in health check should be working:

```bash
docker inspect cron-query-service | grep -A 10 Health
```

**Expected output:**
```json
"Health": {
    "Status": "healthy",
    "FailingStreak": 0,
    "Log": [...]
}
```

## Troubleshooting

### Container Won't Start

Check logs for errors:
```bash
docker logs cron-query-service
```

Common issues:
- Port 8080 already in use: Use `-p 8081:8080` to map to different host port
- Groovy JAR not found: Ensure the JAR was downloaded during build
- Permission issues: Ensure the test_crontab.txt file is readable

### Health Check Fails

If health check shows "unhealthy":

1. Check if the application started:
   ```bash
   docker logs cron-query-service | grep "Started CronQueryServiceApplication"
   ```

2. Check if port is accessible:
   ```bash
   docker exec cron-query-service curl -f http://localhost:8080/actuator/health
   ```

3. Verify crontab file is accessible:
   ```bash
   docker exec cron-query-service cat /app/test_crontab.txt
   ```

### API Returns Empty Results

Verify the crontab file is loaded:

```bash
curl http://localhost:8080/actuator/health | jq '.components.crontabLoader.details'
```

Should show:
- `sources`: ["test_file"]
- `jobCount`: > 0

### Volume Mount Issues (Windows)

On Windows with Docker Desktop, ensure:
- File sharing is enabled in Docker Desktop settings
- Use absolute paths or proper relative paths
- Use forward slashes in paths

Example for Windows:
```bash
docker run -d \
  --name cron-query-service \
  -p 8080:8080 \
  -v "C:/path/to/project/test_crontab.txt:/app/test_crontab.txt:ro" \
  cron-query-service:latest
```

## Performance Testing

### Load Testing with curl

Simple load test:
```bash
for i in {1..100}; do
  curl -s "http://localhost:8080/api/jobs?query=jobs+on+weekdays" > /dev/null &
done
wait
```

Check metrics:
```bash
curl http://localhost:8080/actuator/prometheus | grep cronquery_requests
```

### Memory Usage

Monitor container resource usage:
```bash
docker stats cron-query-service
```

Expected resource usage:
- Memory: 200-400MB
- CPU: < 5% at idle, spikes during queries

## Cleanup

### Stop and Remove Container

```bash
docker stop cron-query-service
docker rm cron-query-service
```

### Using Docker Compose

```bash
docker-compose down
```

### Remove Image

```bash
docker rmi cron-query-service:latest
```

### Clean All Docker Resources

```bash
docker system prune -a
```

## Validation Checklist

Use this checklist to verify the Docker image is working correctly:

- [ ] Image builds successfully without errors
- [ ] Container starts and reaches "healthy" status
- [ ] Health check endpoint returns HTTP 200 with status "UP"
- [ ] Crontab loader shows correct job count
- [ ] Groovy JAR integration is working
- [ ] API endpoint responds to natural language queries
- [ ] API endpoint responds to structured queries
- [ ] JSON format output is valid
- [ ] CSV format output is valid
- [ ] YAML format output is valid
- [ ] Swagger UI is accessible and functional
- [ ] Prometheus metrics endpoint is working
- [ ] Volume-mounted crontab file is loaded correctly
- [ ] Container health check passes
- [ ] Container can be stopped and restarted successfully

## Requirements Validation

This testing validates the following requirements:

- **Requirement 4.1**: Dockerfile builds a runnable container image ✓
- **Requirement 4.2**: Docker image includes test crontab file ✓
- **Requirement 4.3**: Service exposes on configurable port (8080) ✓
- **Requirement 4.4**: Multi-stage build minimizes image size ✓
- **Requirement 4.5**: Health check configuration included ✓

## Next Steps

After successful Docker testing:

1. Push image to container registry (Docker Hub, ECR, etc.)
2. Deploy to Kubernetes or container orchestration platform
3. Set up CI/CD pipeline for automated builds
4. Configure production monitoring and alerting
5. Set up log aggregation (ELK, Splunk, etc.)
