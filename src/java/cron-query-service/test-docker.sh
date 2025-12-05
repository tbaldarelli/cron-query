#!/bin/bash
# Docker Testing Script for cron-query-service
# This script automates the Docker build and testing process

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
IMAGE_NAME="cron-query-service"
IMAGE_TAG="latest"
CONTAINER_NAME="cron-query-service-test"
HOST_PORT="8080"
CONTAINER_PORT="8080"
STARTUP_TIMEOUT=60

# Function to print colored output
print_status() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

print_info() {
    echo -e "${YELLOW}[i]${NC} $1"
}

# Function to cleanup
cleanup() {
    print_info "Cleaning up..."
    docker stop ${CONTAINER_NAME} 2>/dev/null || true
    docker rm ${CONTAINER_NAME} 2>/dev/null || true
}

# Trap to ensure cleanup on exit
trap cleanup EXIT

# Step 1: Build Docker image
print_info "Building Docker image: ${IMAGE_NAME}:${IMAGE_TAG}"
docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .

if [ $? -eq 0 ]; then
    print_status "Docker image built successfully"
else
    print_error "Docker image build failed"
    exit 1
fi

# Step 2: Check image size
IMAGE_SIZE=$(docker images ${IMAGE_NAME}:${IMAGE_TAG} --format "{{.Size}}")
print_info "Image size: ${IMAGE_SIZE}"

# Step 3: Run container
print_info "Starting container: ${CONTAINER_NAME}"
docker run -d \
    --name ${CONTAINER_NAME} \
    -p ${HOST_PORT}:${CONTAINER_PORT} \
    -v "$(pwd)/../../../test_crontab.txt:/app/test_crontab.txt:ro" \
    -e CRONTAB_TEST_FILE=/app/test_crontab.txt \
    ${IMAGE_NAME}:${IMAGE_TAG}

if [ $? -eq 0 ]; then
    print_status "Container started successfully"
else
    print_error "Failed to start container"
    exit 1
fi

# Step 4: Wait for application to start
print_info "Waiting for application to start (timeout: ${STARTUP_TIMEOUT}s)..."
ELAPSED=0
while [ $ELAPSED -lt $STARTUP_TIMEOUT ]; do
    if docker logs ${CONTAINER_NAME} 2>&1 | grep -q "Started CronQueryServiceApplication"; then
        print_status "Application started successfully"
        break
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    echo -n "."
done
echo ""

if [ $ELAPSED -ge $STARTUP_TIMEOUT ]; then
    print_error "Application failed to start within timeout"
    docker logs ${CONTAINER_NAME}
    exit 1
fi

# Step 5: Test health check endpoint
print_info "Testing health check endpoint..."
sleep 5  # Give health indicators time to initialize

HEALTH_RESPONSE=$(curl -s http://localhost:${HOST_PORT}/actuator/health)
HEALTH_STATUS=$(echo $HEALTH_RESPONSE | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ "$HEALTH_STATUS" = "UP" ]; then
    print_status "Health check passed: $HEALTH_STATUS"
    echo "$HEALTH_RESPONSE" | jq '.' 2>/dev/null || echo "$HEALTH_RESPONSE"
else
    print_error "Health check failed: $HEALTH_STATUS"
    echo "$HEALTH_RESPONSE"
    exit 1
fi

# Step 6: Test API endpoint with natural language query
print_info "Testing API with natural language query..."
API_RESPONSE=$(curl -s "http://localhost:${HOST_PORT}/api/jobs?query=jobs+on+weekdays")
JOB_COUNT=$(echo $API_RESPONSE | jq '.totalCount' 2>/dev/null)

if [ ! -z "$JOB_COUNT" ] && [ "$JOB_COUNT" -gt 0 ]; then
    print_status "API query successful: Found $JOB_COUNT jobs"
else
    print_error "API query failed or returned no jobs"
    echo "$API_RESPONSE"
    exit 1
fi

# Step 7: Test API endpoint with structured query
print_info "Testing API with structured query..."
API_RESPONSE=$(curl -s "http://localhost:${HOST_PORT}/api/jobs?time=07:00")
JOB_COUNT=$(echo $API_RESPONSE | jq '.totalCount' 2>/dev/null)

if [ ! -z "$JOB_COUNT" ]; then
    print_status "Structured query successful: Found $JOB_COUNT jobs"
else
    print_error "Structured query failed"
    echo "$API_RESPONSE"
    exit 1
fi

# Step 8: Test CSV format
print_info "Testing CSV format output..."
CSV_RESPONSE=$(curl -s "http://localhost:${HOST_PORT}/api/jobs?query=jobs+on+weekdays&format=csv")

if echo "$CSV_RESPONSE" | grep -q "Schedule,Command"; then
    print_status "CSV format working"
else
    print_error "CSV format failed"
    echo "$CSV_RESPONSE"
    exit 1
fi

# Step 9: Test YAML format
print_info "Testing YAML format output..."
YAML_RESPONSE=$(curl -s "http://localhost:${HOST_PORT}/api/jobs?query=jobs+on+weekends&format=yaml")

if echo "$YAML_RESPONSE" | grep -q "jobs:"; then
    print_status "YAML format working"
else
    print_error "YAML format failed"
    echo "$YAML_RESPONSE"
    exit 1
fi

# Step 10: Test Swagger UI
print_info "Testing Swagger UI..."
SWAGGER_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:${HOST_PORT}/swagger-ui.html)

if [ "$SWAGGER_RESPONSE" = "200" ]; then
    print_status "Swagger UI accessible"
else
    print_error "Swagger UI not accessible (HTTP $SWAGGER_RESPONSE)"
    exit 1
fi

# Step 11: Test Prometheus metrics
print_info "Testing Prometheus metrics..."
METRICS_RESPONSE=$(curl -s http://localhost:${HOST_PORT}/actuator/prometheus)

if echo "$METRICS_RESPONSE" | grep -q "jvm_memory_used_bytes"; then
    print_status "Prometheus metrics working"
else
    print_error "Prometheus metrics failed"
    exit 1
fi

# Step 12: Verify Docker health check
print_info "Verifying Docker health check..."
sleep 35  # Wait for first health check to run

DOCKER_HEALTH=$(docker inspect ${CONTAINER_NAME} --format='{{.State.Health.Status}}')

if [ "$DOCKER_HEALTH" = "healthy" ]; then
    print_status "Docker health check passed: $DOCKER_HEALTH"
else
    print_error "Docker health check failed: $DOCKER_HEALTH"
    docker inspect ${CONTAINER_NAME} --format='{{json .State.Health}}' | jq '.'
    exit 1
fi

# Step 13: Test volume-mounted crontab
print_info "Verifying volume-mounted crontab..."
CRONTAB_CONTENT=$(docker exec ${CONTAINER_NAME} head -5 /app/test_crontab.txt)

if [ ! -z "$CRONTAB_CONTENT" ]; then
    print_status "Volume-mounted crontab accessible"
else
    print_error "Volume-mounted crontab not accessible"
    exit 1
fi

# Step 14: Check container logs for errors
print_info "Checking container logs for errors..."
ERROR_COUNT=$(docker logs ${CONTAINER_NAME} 2>&1 | grep -i "error" | grep -v "0 errors" | wc -l)

if [ "$ERROR_COUNT" -eq 0 ]; then
    print_status "No errors found in logs"
else
    print_error "Found $ERROR_COUNT error messages in logs"
    docker logs ${CONTAINER_NAME} 2>&1 | grep -i "error" | head -10
fi

# Step 15: Resource usage check
print_info "Checking resource usage..."
docker stats ${CONTAINER_NAME} --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}"

# Summary
echo ""
echo "=========================================="
echo "         Docker Testing Summary"
echo "=========================================="
print_status "Image built successfully"
print_status "Container started and healthy"
print_status "Health check endpoint working"
print_status "API endpoints responding correctly"
print_status "Multiple output formats working (JSON, CSV, YAML)"
print_status "Swagger UI accessible"
print_status "Prometheus metrics available"
print_status "Docker health check passing"
print_status "Volume-mounted crontab working"
echo ""
print_status "All tests passed! ✓"
echo ""
echo "Container is running at: http://localhost:${HOST_PORT}"
echo "Swagger UI: http://localhost:${HOST_PORT}/swagger-ui.html"
echo "Health Check: http://localhost:${HOST_PORT}/actuator/health"
echo ""
echo "To view logs: docker logs -f ${CONTAINER_NAME}"
echo "To stop: docker stop ${CONTAINER_NAME}"
echo "To remove: docker rm ${CONTAINER_NAME}"
echo ""
