# Docker Deployment Guide

This guide explains how to build and run the cron-query-service using Docker.

## Prerequisites

- Docker 20.10 or later
- Docker Compose 2.0 or later (optional, for docker-compose deployment)

## Quick Start with Docker Compose

The easiest way to run the service is using Docker Compose:

```bash
# From the cron-query-service directory
docker-compose up -d
```

This will:
- Build the Docker image
- Start the container
- Map port 8080 to your host
- Mount the test crontab file from the project root

Access the service at:
- API: http://localhost:8080/api/jobs
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health Check: http://localhost:8080/actuator/health

## Building the Docker Image

### Using Docker Compose

```bash
docker-compose build
```

### Using Docker directly

```bash
# From the cron-query-service directory
docker build -t cron-query-service:1.2.2 .
```

## Running the Container

### Using Docker Compose

```bash
# Start in detached mode
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the service
docker-compose down
```

### Using Docker directly

```bash
# Run with default settings
docker run -d \
  --name cron-query-service \
  -p 8080:8080 \
  -v "$(pwd)/../../../test_crontab.txt:/app/test_crontab.txt:ro" \
  cron-query-service:1.2.2

# Run with custom port
docker run -d \
  --name cron-query-service \
  -p 9090:8080 \
  -e SERVER_PORT=8080 \
  -v "$(pwd)/../../../test_crontab.txt:/app/test_crontab.txt:ro" \
  cron-query-service:1.2.2

# Run with custom crontab file
docker run -d \
  --name cron-query-service \
  -p 8080:8080 \
  -v "/path/to/your/crontab.txt:/app/test_crontab.txt:ro" \
  -e CRONTAB_TEST_FILE=/app/test_crontab.txt \
  cron-query-service:1.2.2
```

## Configuration

### Environment Variables

Configure the service using environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Port the service listens on |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile (dev, prod) |
| `CRONTAB_TEST_FILE` | `/app/test_crontab.txt` | Path to test crontab file |
| `LOGGING_LEVEL_ROOT` | `INFO` | Root logging level |
| `LOGGING_LEVEL_COM_CRONQUERY` | `DEBUG` | Application logging level |
| `CORS_ALLOWED_ORIGINS` | `*` | CORS allowed origins |

### Using .env file with Docker Compose

Create a `.env` file in the cron-query-service directory:

```bash
# Copy the example file
cp .env.example .env

# Edit as needed
nano .env
```

Example `.env` file:
```
HOST_PORT=8080
SPRING_PROFILE=dev
LOG_LEVEL=INFO
APP_LOG_LEVEL=DEBUG
CORS_ORIGINS=http://localhost:3000,http://localhost:4200
```

## Volume Mounts

### Test Crontab File

The container expects a crontab file at `/app/test_crontab.txt`. You can mount your own:

```bash
# Using docker-compose (edit docker-compose.yml or use .env)
CUSTOM_CRONTAB=/path/to/your/crontab.txt docker-compose up

# Using docker run
docker run -d \
  -p 8080:8080 \
  -v "/path/to/your/crontab.txt:/app/test_crontab.txt:ro" \
  cron-query-service:1.2.2
```

### System Crontab Access (Linux only)

To access real system crontabs, mount them into the container:

```bash
docker run -d \
  -p 8080:8080 \
  -v "/etc/crontab:/etc/crontab:ro" \
  -v "/etc/cron.d:/etc/cron.d:ro" \
  -e SPRING_PROFILES_ACTIVE=prod \
  cron-query-service:1.2.2
```

## Health Checks

The container includes a built-in health check that runs every 30 seconds:

```bash
# Check container health status
docker ps

# View health check logs
docker inspect --format='{{json .State.Health}}' cron-query-service | jq
```

The health check endpoint is: `http://localhost:8080/actuator/health`

## Multi-Stage Build Details

The Dockerfile uses a multi-stage build for optimization:

1. **Build Stage** (`maven:3.9-eclipse-temurin-21`)
   - Downloads the Groovy JAR from GitHub releases
   - Compiles the Spring Boot application
   - Creates the executable JAR

2. **Runtime Stage** (`eclipse-temurin:21-jre-jammy`)
   - Uses minimal JRE image (smaller size)
   - Copies only the necessary JARs
   - Runs as non-root user for security
   - Includes health check configuration

## Troubleshooting

### Container won't start

Check the logs:
```bash
docker-compose logs cron-query-service
# or
docker logs cron-query-service
```

### Health check failing

Verify the service is responding:
```bash
docker exec cron-query-service curl -f http://localhost:8080/actuator/health
```

### Groovy JAR not found

The Groovy JAR is downloaded during the Maven build. If the download fails:

1. Check your internet connection
2. Verify the GitHub release exists: https://github.com/tbaldarelli/cron-query/releases/tag/v1.2.2
3. Manually download and place in `lib/` directory before building

### Port already in use

Change the host port mapping:
```bash
# Using docker-compose
HOST_PORT=9090 docker-compose up

# Using docker run
docker run -d -p 9090:8080 cron-query-service:1.2.2
```

### Permission denied on crontab file

Ensure the mounted crontab file is readable:
```bash
chmod 644 /path/to/your/crontab.txt
```

## Production Deployment

For production deployments:

1. Use the `prod` profile:
   ```bash
   docker run -d \
     -p 8080:8080 \
     -e SPRING_PROFILES_ACTIVE=prod \
     cron-query-service:1.2.2
   ```

2. Configure proper CORS origins:
   ```bash
   -e CORS_ALLOWED_ORIGINS=https://yourdomain.com
   ```

3. Mount real crontab sources (Linux):
   ```bash
   -v /etc/crontab:/etc/crontab:ro \
   -v /etc/cron.d:/etc/cron.d:ro
   ```

4. Use a reverse proxy (nginx, traefik) for SSL termination

5. Set appropriate resource limits:
   ```bash
   docker run -d \
     --memory="512m" \
     --cpus="1.0" \
     cron-query-service:1.2.2
   ```

## Monitoring

### Prometheus Metrics

Metrics are exposed at `/actuator/prometheus`:

```bash
curl http://localhost:8080/actuator/prometheus
```

### Application Info

Build and version information:

```bash
curl http://localhost:8080/actuator/info
```

## Cleanup

Remove containers and images:

```bash
# Using docker-compose
docker-compose down
docker-compose down --rmi all  # Also remove images

# Using docker
docker stop cron-query-service
docker rm cron-query-service
docker rmi cron-query-service:1.2.2
```

## Advanced Usage

### Custom Build Arguments

Pass build arguments during image creation:

```bash
docker build \
  --build-arg MAVEN_OPTS="-Xmx1024m" \
  -t cron-query-service:1.2.2 .
```

### Docker Compose Override

Create a `docker-compose.override.yml` for local customizations:

```yaml
version: '3.8'
services:
  cron-query-service:
    ports:
      - "9090:8080"
    environment:
      - LOGGING_LEVEL_COM_CRONQUERY=TRACE
```

### Network Configuration

Connect to existing Docker networks:

```bash
docker run -d \
  --name cron-query-service \
  --network my-existing-network \
  -p 8080:8080 \
  cron-query-service:1.2.2
```
