# Docker Quick Start Guide

## TL;DR - Get Running in 2 Minutes

```bash
# 1. Build
docker build -t cron-query-service:latest .

# 2. Run
docker run -d --name cron-query-service -p 8080:8080 cron-query-service:latest

# 3. Test
curl http://localhost:8080/actuator/health
curl "http://localhost:8080/api/jobs?query=all+jobs"

# 4. View
open http://localhost:8080/swagger-ui.html
```

## Automated Testing

**Linux/Mac:**
```bash
./test-docker.sh
```

**Windows:**
```powershell
.\test-docker.ps1
```

## Common Commands

### Build & Run
```bash
# Build image
docker build -t cron-query-service:latest .

# Run container
docker run -d --name cron-query-service -p 8080:8080 cron-query-service:latest

# Run with custom crontab
docker run -d --name cron-query-service -p 8080:8080 \
  -v "$(pwd)/../../../test_crontab.txt:/app/test_crontab.txt:ro" \
  cron-query-service:latest
```

### Docker Compose
```bash
# Start
docker-compose up -d

# View logs
docker-compose logs -f

# Stop
docker-compose down
```

### Monitoring
```bash
# View logs
docker logs -f cron-query-service

# Check health
docker ps
curl http://localhost:8080/actuator/health

# Resource usage
docker stats cron-query-service
```

### Cleanup
```bash
# Stop and remove
docker stop cron-query-service
docker rm cron-query-service

# Remove image
docker rmi cron-query-service:latest
```

## Quick Tests

### Health Check
```bash
curl http://localhost:8080/actuator/health | jq '.'
```

### Query Jobs
```bash
# Natural language
curl "http://localhost:8080/api/jobs?query=jobs+on+weekdays"

# Structured
curl "http://localhost:8080/api/jobs?time=07:00"

# CSV format
curl "http://localhost:8080/api/jobs?query=all+jobs&format=csv"
```

### Swagger UI
```
http://localhost:8080/swagger-ui.html
```

### Metrics
```bash
curl http://localhost:8080/actuator/prometheus | grep cronquery
```

## Troubleshooting

### Container won't start
```bash
docker logs cron-query-service
```

### Port already in use
```bash
docker run -d --name cron-query-service -p 8081:8080 cron-query-service:latest
```

### Health check fails
```bash
docker exec cron-query-service curl http://localhost:8080/actuator/health
```

### View crontab file
```bash
docker exec cron-query-service cat /app/test_crontab.txt
```

## Environment Variables

```bash
docker run -d --name cron-query-service -p 8080:8080 \
  -e SERVER_PORT=8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e LOGGING_LEVEL_COM_CRONQUERY=DEBUG \
  cron-query-service:latest
```

## Full Documentation

- **Comprehensive Guide**: [DOCKER_TESTING.md](DOCKER_TESTING.md)
- **Validation Checklist**: [DOCKER_VALIDATION_CHECKLIST.md](DOCKER_VALIDATION_CHECKLIST.md)
- **Main README**: [README.md](README.md)

## Expected Results

- **Build Time**: 3-5 minutes (first build)
- **Image Size**: 400-500MB
- **Startup Time**: 30-40 seconds
- **Memory Usage**: 200-400MB
- **Health Status**: "healthy" after 40 seconds
