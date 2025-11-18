# Multi-stage Dockerfile for cron-query-service
# Stage 1: Build stage with Maven and JDK
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy Maven configuration
COPY pom.xml .

# Copy lib directory with Groovy JAR (must exist before Maven build)
COPY lib ./lib

# Copy source code
COPY src ./src

# Download dependencies and build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage with JRE
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/cron-query-service-*.jar app.jar

# Copy the Groovy JAR from builder stage (needed at runtime due to system scope)
COPY --from=builder /app/lib/cron-query-groovy-*.jar lib/

# Copy test crontab file for development/testing
# The file will be mounted as a volume by docker-compose from the project root
# For standalone builds, create a minimal fallback crontab
RUN echo "# Fallback test crontab - will be overridden by volume mount" > /app/test_crontab.txt && \
    echo "0 8 * * 1-5 /usr/bin/backup.sh" >> /app/test_crontab.txt && \
    echo "30 12 * * * /usr/bin/lunch.sh" >> /app/test_crontab.txt && \
    echo "0 0 * * 0 /usr/bin/weekly-report.sh" >> /app/test_crontab.txt

# Create a non-root user for security
RUN groupadd -r cronquery && useradd -r -g cronquery cronquery && \
    chown -R cronquery:cronquery /app

# Switch to non-root user
USER cronquery

# Expose the application port
EXPOSE 8080

# Configure health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Set default environment variables
ENV SERVER_PORT=8080 \
    CRONTAB_TEST_FILE=/app/test_crontab.txt \
    SPRING_PROFILES_ACTIVE=dev

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
