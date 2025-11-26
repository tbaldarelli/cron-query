package com.cronquery.service.controller;

import com.cronquery.service.CronQueryService;
import com.cronquery.service.exception.CrontabLoadException;
import com.cronquery.service.exception.InvalidQueryException;
import com.cronquery.service.model.CronJob;
import com.cronquery.service.model.OutputFormat;
import com.cronquery.service.model.QueryResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CronQueryController.
 * Tests request validation, response formatting, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class CronQueryControllerTest {

    @Mock
    private CronQueryService cronQueryService;

    @Mock
    private Counter apiRequestCounter;

    @Mock
    private Timer apiRequestTimer;

    private CronQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new CronQueryController(cronQueryService, apiRequestCounter, apiRequestTimer);
        
        // Mock timer to execute the supplier immediately
        when(apiRequestTimer.record(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    void testQueryJobs_WithNaturalLanguageQuery_ReturnsJsonResponse() {
        // Arrange
        String query = "jobs on Saturday";
        QueryResponse mockResponse = createMockQueryResponse();
        when(cronQueryService.executeQuery(any())).thenReturn(mockResponse);

        // Act
        ResponseEntity<String> response = controller.queryJobs(query, null, null, null, "json");

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"totalCount\" : 2"));
        assertTrue(response.getBody().contains("backup.sh"));
        verify(apiRequestCounter).increment();
        verify(cronQueryService).executeQuery(any());
    }

    @Test
    void testQueryJobs_WithStructuredQuery_ReturnsJsonResponse() {
        // Arrange
        QueryResponse mockResponse = createMockQueryResponse();
        when(cronQueryService.executeQuery(any())).thenReturn(mockResponse);

        // Act
        ResponseEntity<String> response = controller.queryJobs(null, "Monday", "08:00", null, "json");

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"totalCount\" : 2"));
        verify(cronQueryService).executeQuery(any());
    }

    @Test
    void testQueryJobs_WithCsvFormat_ReturnsCsvResponse() {
        // Arrange
        QueryResponse mockResponse = createMockQueryResponse();
        when(cronQueryService.executeQuery(any())).thenReturn(mockResponse);

        // Act
        ResponseEntity<String> response = controller.queryJobs("jobs on Saturday", null, null, null, "csv");

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
        assertTrue(response.getBody().contains("Schedule,Command,Source,User,Description,Next Runs"));
        assertTrue(response.getBody().contains("backup.sh"));
    }

    @Test
    void testQueryJobs_WithYamlFormat_ReturnsYamlResponse() {
        // Arrange
        QueryResponse mockResponse = createMockQueryResponse();
        when(cronQueryService.executeQuery(any())).thenReturn(mockResponse);

        // Act
        ResponseEntity<String> response = controller.queryJobs("jobs on Saturday", null, null, null, "yaml");

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/x-yaml", response.getHeaders().getContentType().toString());
        assertTrue(response.getBody().contains("totalCount: 2"));
    }

    @Test
    void testQueryJobs_WithInvalidQuery_ThrowsInvalidQueryException() {
        // Arrange
        when(cronQueryService.executeQuery(any())).thenThrow(new InvalidQueryException("Invalid query"));

        // Act & Assert
        assertThrows(InvalidQueryException.class, () -> {
            controller.queryJobs(null, null, null, null, "json");
        });
    }

    @Test
    void testQueryJobs_WithCrontabLoadFailure_ThrowsCrontabLoadException() {
        // Arrange
        when(cronQueryService.executeQuery(any())).thenThrow(new CrontabLoadException("Failed to load crontab"));

        // Act & Assert
        assertThrows(CrontabLoadException.class, () -> {
            controller.queryJobs("jobs on Saturday", null, null, null, "json");
        });
    }

    @Test
    void testQueryJobs_WithTimeRangeQuery_ReturnsJsonResponse() {
        // Arrange
        QueryResponse mockResponse = createMockQueryResponse();
        when(cronQueryService.executeQuery(any())).thenReturn(mockResponse);

        // Act
        ResponseEntity<String> response = controller.queryJobs(null, null, null, "08:00-17:00", "json");

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(cronQueryService).executeQuery(any());
    }

    @Test
    void testCsvEscaping_WithCommasInCommand() {
        // Arrange
        QueryResponse mockResponse = new QueryResponse();
        CronJob job = new CronJob();
        job.setSchedule("0 8 * * 6");
        job.setCommand("/usr/bin/backup.sh --option1 value1, --option2 value2");
        job.setSource("/etc/crontab");
        job.setUser("root");
        job.setDescription("Test job");
        mockResponse.setJobs(Arrays.asList(job));
        mockResponse.setTotalCount(1);
        
        when(cronQueryService.executeQuery(any())).thenReturn(mockResponse);

        // Act
        ResponseEntity<String> response = controller.queryJobs("test", null, null, null, "csv");

        // Assert
        assertNotNull(response);
        assertTrue(response.getBody().contains("\""));  // Should be quoted due to comma
    }

    @Test
    void testCsvEscaping_WithQuotesInCommand() {
        // Arrange
        QueryResponse mockResponse = new QueryResponse();
        CronJob job = new CronJob();
        job.setSchedule("0 8 * * 6");
        job.setCommand("/usr/bin/echo \"Hello World\"");
        job.setSource("/etc/crontab");
        job.setUser("root");
        job.setDescription("Test job");
        mockResponse.setJobs(Arrays.asList(job));
        mockResponse.setTotalCount(1);
        
        when(cronQueryService.executeQuery(any())).thenReturn(mockResponse);

        // Act
        ResponseEntity<String> response = controller.queryJobs("test", null, null, null, "csv");

        // Assert
        assertNotNull(response);
        assertTrue(response.getBody().contains("\"\""));  // Quotes should be escaped
    }

    /**
     * Helper method to create a mock QueryResponse for testing.
     */
    private QueryResponse createMockQueryResponse() {
        QueryResponse response = new QueryResponse();
        
        CronJob job1 = new CronJob();
        job1.setSchedule("0 8 * * 6");
        job1.setCommand("/usr/bin/backup.sh");
        job1.setSource("/etc/crontab");
        job1.setUser("root");
        job1.setDescription("At 08:00 on Saturday");
        job1.setNextRuns(Arrays.asList("2024-11-16 08:00:00", "2024-11-23 08:00:00"));
        
        CronJob job2 = new CronJob();
        job2.setSchedule("0 12 * * 6");
        job2.setCommand("/usr/bin/cleanup.sh");
        job2.setSource("/etc/crontab");
        job2.setUser("root");
        job2.setDescription("At 12:00 on Saturday");
        job2.setNextRuns(Arrays.asList("2024-11-16 12:00:00", "2024-11-23 12:00:00"));
        
        response.setJobs(Arrays.asList(job1, job2));
        response.setTotalCount(2);
        response.setQuery("jobs on Saturday");
        response.setSources(Arrays.asList("/etc/crontab"));
        response.setExecutionTimeMs(45);
        
        return response;
    }
}
