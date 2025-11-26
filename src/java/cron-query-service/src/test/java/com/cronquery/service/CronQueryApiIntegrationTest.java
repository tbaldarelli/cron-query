package com.cronquery.service;

import com.cronquery.service.model.QueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API Integration Tests for CronQueryService.
 * Tests full request/response cycle with actual Spring Boot application context.
 * Uses test crontab file for consistent results.
 * 
 * Requirements: 1.1, 1.2, 1.3
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CronQueryApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/jobs";
    }

    @Test
    void testQueryJobs_WithNaturalLanguageQuery_ReturnsJsonResponse() throws Exception {
        // Act
        String url = getBaseUrl() + "?query=jobs at 3 am&format=json";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        QueryResponse queryResponse = objectMapper.readValue(response.getBody(), QueryResponse.class);
        assertNotNull(queryResponse);
        assertTrue(queryResponse.getTotalCount() >= 0);
        assertNotNull(queryResponse.getJobs());
        assertEquals("jobs at 3 am", queryResponse.getQuery());
    }

    @Test
    void testQueryJobs_WithDayQuery_ReturnsMatchingJobs() throws Exception {
        // Act
        String url = getBaseUrl() + "?day=Monday&format=json";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        QueryResponse queryResponse = objectMapper.readValue(response.getBody(), QueryResponse.class);
        assertNotNull(queryResponse);
        assertTrue(queryResponse.getTotalCount() >= 0);
        assertNotNull(queryResponse.getJobs());
    }

    @Test
    void testQueryJobs_WithTimeQuery_ReturnsMatchingJobs() throws Exception {
        // Act
        String url = getBaseUrl() + "?time=07:00&format=json";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        QueryResponse queryResponse = objectMapper.readValue(response.getBody(), QueryResponse.class);
        assertNotNull(queryResponse);
        assertTrue(queryResponse.getTotalCount() >= 0);
    }

    @Test
    void testQueryJobs_WithTimeRangeQuery_ReturnsMatchingJobs() throws Exception {
        // Act
        String url = getBaseUrl() + "?timeRange=07:00-18:00&format=json";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        QueryResponse queryResponse = objectMapper.readValue(response.getBody(), QueryResponse.class);
        assertNotNull(queryResponse);
        assertTrue(queryResponse.getTotalCount() >= 0);
    }

    @Test
    void testQueryJobs_WithCombinedDayAndTime_ReturnsMatchingJobs() throws Exception {
        // Act
        String url = getBaseUrl() + "?day=Saturday&time=19:00&format=json";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        QueryResponse queryResponse = objectMapper.readValue(response.getBody(), QueryResponse.class);
        assertNotNull(queryResponse);
        assertTrue(queryResponse.getTotalCount() >= 0);
    }

    @Test
    void testQueryJobs_WithCsvFormat_ReturnsCsvResponse() {
        // Act
        String url = getBaseUrl() + "?query=jobs at 7 am&format=csv";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("Schedule,Command,Source"));
    }

    @Test
    void testQueryJobs_WithYamlFormat_ReturnsYamlResponse() {
        // Act
        String url = getBaseUrl() + "?query=jobs at 7 am&format=yaml";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("totalCount:") || body.contains("jobs:"));
    }

    @Test
    void testQueryJobs_WithNoParameters_ReturnsBadRequest() {
        // Act
        String url = getBaseUrl();
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testQueryJobs_WithInvalidFormat_UsesDefaultJson() throws Exception {
        // Act
        String url = getBaseUrl() + "?query=jobs at 7 am&format=invalid";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Should return JSON by default
        QueryResponse queryResponse = objectMapper.readValue(response.getBody(), QueryResponse.class);
        assertNotNull(queryResponse);
    }

    @Test
    void testHealthEndpoint_ReturnsHealthStatus() {
        // Act
        String url = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("UP") || body.contains("DOWN"));
    }

    @Test
    void testInfoEndpoint_ReturnsApplicationInfo() {
        // Act
        String url = "http://localhost:" + port + "/actuator/info";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

}
