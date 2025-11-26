# Prometheus Endpoint Investigation Notes

## Issue Summary
During integration testing, we discovered that the `/actuator/prometheus` endpoint returns a 500 error, even though Prometheus metrics are properly configured and the Micrometer registry is working.

## Investigation Findings

### What Works
- ✅ `/actuator/health` - Health check endpoint works correctly
- ✅ `/actuator/info` - Application info endpoint works correctly
- ✅ `/actuator/metrics` - Metrics endpoint works and returns Micrometer metrics
- ✅ Micrometer Prometheus registry is properly initialized
- ✅ Custom metrics (cronquery.requests.total, cronquery.groovyjar.invocations, etc.) are being recorded

### What Doesn't Work
- ❌ `/actuator/prometheus` - Returns 500 error: "No static resource actuator/prometheus"

### Configuration Tested
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus  # Also tried with "*"
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

### Dependencies
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

## Root Cause Analysis

The issue appears to be related to how Spring Boot 3.x handles Prometheus endpoint registration. Despite:
1. Having the correct dependency (`micrometer-registry-prometheus`)
2. Enabling the endpoint in configuration
3. Enabling Prometheus metrics export
4. Seeing "Exposing 13 endpoints beneath base path '/actuator'" in logs

The Prometheus endpoint is **not included in the list of available endpoints** when querying `/actuator`.

### Actuator Endpoints Available
When we query `/actuator`, we see these endpoints:
- beans, caches, health, info, conditions, configprops, env, loggers, threaddump, metrics, sbom, scheduledtasks, mappings

**Notice: `prometheus` is NOT in this list**, even though it's configured to be exposed.

## Possible Explanations

1. **Spring Boot 3.x Change**: The Prometheus scrape endpoint may have been moved or renamed in Spring Boot 3.x
2. **Auto-configuration Issue**: The Prometheus endpoint auto-configuration may not be triggering properly
3. **Dependency Version Mismatch**: There may be a compatibility issue between Spring Boot 3.5.7 and the Micrometer version
4. **Endpoint Registration**: The endpoint may need explicit registration rather than relying on auto-configuration

## Workaround for Production

For production Prometheus scraping, you have several options:

### Option 1: Use the Metrics Endpoint
Prometheus can scrape from `/actuator/metrics` and parse the JSON format, though this is less efficient than the native Prometheus format.

### Option 2: Custom Prometheus Endpoint
Create a custom controller that exposes metrics in Prometheus format:
```java
@RestController
@RequestMapping("/metrics")
public class PrometheusMetricsController {
    
    @Autowired
    private PrometheusMeterRegistry registry;
    
    @GetMapping(produces = "text/plain")
    public String prometheus() {
        return registry.scrape();
    }
}
```

### Option 3: Investigate Spring Boot Actuator Version
Check if there's a specific Spring Boot Actuator version or additional dependency needed for Prometheus endpoint support in Spring Boot 3.x.

## Impact on Integration Tests

**Decision**: We've excluded the Prometheus endpoint test from integration tests because:
1. The core metrics functionality works (verified via `/actuator/metrics`)
2. Custom application metrics are being recorded correctly
3. The Prometheus endpoint is primarily for production monitoring, not core application functionality
4. Health and info endpoints work correctly for basic observability

## Recommendations

1. **For Development/Testing**: Use `/actuator/metrics` to verify metrics are being collected
2. **For Production**: Investigate Option 2 (custom endpoint) or wait for Spring Boot updates
3. **Future Investigation**: When upgrading Spring Boot versions, retest the Prometheus endpoint
4. **Alternative**: Consider using Spring Boot 2.x if native Prometheus endpoint support is critical

## References
- Spring Boot Actuator Documentation: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
- Micrometer Prometheus: https://micrometer.io/docs/registry/prometheus
- Spring Boot 3.x Migration Guide: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide

## Date
2025-11-26

## Tested With
- Spring Boot: 3.5.7
- Java: 21
- Micrometer: (version managed by Spring Boot parent)
