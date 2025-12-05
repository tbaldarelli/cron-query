# CI/CD Integration for Spring Boot Microservice

This document describes the CI/CD pipeline integration for the cron-query Spring Boot microservice.

## Overview

The Spring Boot microservice has been integrated into both GitLab CI and GitHub Actions pipelines, providing automated testing, building, and release management alongside the existing Python and Groovy implementations.

## GitLab CI Integration

### Test Stage

**Job: `test_spring_boot`**
- **Image**: `maven:3.9-eclipse-temurin-21`
- **Purpose**: Run unit and integration tests for the Spring Boot microservice
- **Commands**:
  ```bash
  cd src/java/cron-query-service
  mvn clean test
  ```
- **Artifacts**: JUnit test reports and Surefire reports
- **Triggers**: On `master` branch and merge requests

### Release Stage

**Job: `build_release`** (Enhanced)
- **Added**: Maven build step for Spring Boot JAR
- **Commands**:
  ```bash
  cd src/java/cron-query-service && mvn clean package -DskipTests && cd ../../..
  ```
- **Artifacts**: Spring Boot JAR added to release artifacts (`src/java/cron-query-service/target/*.jar`)

**Job: `build_docker`** (New)
- **Image**: `docker:24-dind`
- **Purpose**: Build Docker image for Spring Boot microservice
- **Commands**:
  ```bash
  cd src/java/cron-query-service
  docker build -t cron-query-service:${CI_COMMIT_TAG#v} .
  docker save cron-query-service:${CI_COMMIT_TAG#v} -o ../../../cron-query-service-docker.tar
  ```
- **Artifacts**: Docker image saved as tar file
- **Triggers**: On version tags (e.g., `v1.2.2`)

**Job: `create_release`** (Enhanced)
- **Added**: Spring Boot JAR and Docker image to release assets
- **Dependencies**: Requires both `build_release` and `build_docker` jobs

## GitHub Actions Integration

### Test Workflow (`.github/workflows/test.yml`)

**Job: `test-spring-boot`** (New)
- **Runner**: `ubuntu-latest`
- **Purpose**: Run Maven unit tests
- **Steps**:
  1. Checkout code
  2. Set up JDK 21 with Maven cache
  3. Run `mvn clean test`
  4. Publish JUnit test reports
  5. Upload test results as artifacts
- **Triggers**: On push to `master` and pull requests

**Job: `test-spring-boot-integration`** (New)
- **Runner**: `ubuntu-latest`
- **Purpose**: Run Maven integration tests
- **Steps**:
  1. Checkout code
  2. Set up JDK 21 with Maven cache
  3. Run `mvn clean verify`
  4. Upload integration test results
- **Triggers**: On push to `master` and pull requests

### Release Workflow (`.github/workflows/release.yml`)

**Enhanced Steps**:

1. **Build Spring Boot JAR**
   ```bash
   cd src/java/cron-query-service
   mvn clean package -DskipTests
   ```

2. **Build Docker Image**
   ```bash
   cd src/java/cron-query-service
   docker build -t cron-query-service:${VERSION} .
   docker save cron-query-service:${VERSION} -o ../../cron-query-service-docker-${VERSION}.tar
   ```

3. **Create GitHub Release** (Enhanced)
   - Added Spring Boot JAR to release files: `src/java/cron-query-service/target/cron-query-service-*.jar`
   - Added Docker image tar file: `cron-query-service-docker-*.tar`

**Triggers**: On version tags (e.g., `v1.2.2`)

## Release Artifacts

When a version tag is pushed (e.g., `v1.2.2`), the following artifacts are built and published:

### Python Implementation
- Wheel package (`.whl`)
- Source distribution (`.tar.gz`)

### Groovy Implementation
- Executable JAR (`cron-query-groovy-1.2.2.jar`)

### Spring Boot Microservice (New)
- Executable JAR (`cron-query-service-1.2.2.jar`)
- Docker image tar file (`cron-query-service-docker-1.2.2.tar`)

## Testing Strategy

### Unit Tests
- Run on every push and pull request
- Execute via `mvn clean test`
- Generate JUnit reports for CI integration

### Integration Tests
- Run on every push and pull request
- Execute via `mvn clean verify`
- Test full application context and API endpoints

### Docker Build Tests
- Run only on version tags
- Validate multi-stage Dockerfile build
- Ensure Docker image can be created successfully

## Maven Configuration

The Spring Boot microservice uses Maven with the following key features:

- **Groovy JAR Download**: Automatically downloads the matching version of `cron-query-groovy-${project.version}.jar` from GitHub releases
- **System Scope Dependency**: References the downloaded Groovy JAR
- **Spring Boot Maven Plugin**: Packages the application as an executable JAR with embedded dependencies
- **Surefire Plugin**: Runs unit tests during the test phase

## Docker Build

The Docker build uses a multi-stage approach:

1. **Builder Stage**: Uses `maven:3.9-eclipse-temurin-21` to build the application
2. **Runtime Stage**: Uses `eclipse-temurin:21-jre-jammy` for a smaller runtime image
3. **Health Check**: Configured to check `/actuator/health` endpoint
4. **Non-root User**: Runs as `cronquery` user for security

## Version Synchronization

The Spring Boot microservice version is synchronized with the project version via `.bumpversion.cfg`:

- When the project version is bumped (e.g., from `1.2.2` to `1.2.3`), the POM version is automatically updated
- The Maven download plugin fetches the matching Groovy JAR version from GitHub releases
- This ensures consistency across all implementations

## CI/CD Best Practices

1. **Parallel Testing**: All test jobs run in parallel for faster feedback
2. **Artifact Caching**: Maven dependencies are cached to speed up builds
3. **Test Reports**: JUnit reports are published for easy review
4. **Fail Fast**: Tests run before building release artifacts
5. **Docker Layer Caching**: Multi-stage builds optimize Docker image creation
6. **Security**: Docker images run as non-root user

## Troubleshooting

### Maven Build Fails
- Ensure the Groovy JAR version exists in GitHub releases
- Check that the POM version matches the project version
- Verify Maven download plugin can access GitHub

### Docker Build Fails
- Ensure the Groovy JAR is downloaded before Docker build
- Check that the Dockerfile can access the `lib/` directory
- Verify Docker daemon is available in CI environment

### Tests Fail
- Check test logs in CI artifacts
- Verify test crontab file is accessible
- Ensure all dependencies are properly configured

## Future Enhancements

1. **Docker Registry Push**: Push Docker images to a container registry (Docker Hub, GitHub Container Registry)
2. **Kubernetes Deployment**: Add CI/CD steps for Kubernetes deployment
3. **Performance Testing**: Add performance benchmarks to CI pipeline
4. **Security Scanning**: Integrate container security scanning (Trivy, Snyk)
5. **Code Coverage**: Add code coverage reporting for Spring Boot tests
