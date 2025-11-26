package com.cronquery.service;

import com.cronquery.service.exception.CrontabLoadException;
import com.cronquery.service.exception.GroovyJarException;
import com.cronquery.service.exception.InvalidQueryException;
import com.cronquery.service.integration.CrontabLoader;
import com.cronquery.service.integration.GroovyJarAdapter;
import com.cronquery.service.model.CronJob;
import com.cronquery.service.model.HealthStatus;
import com.cronquery.service.model.OutputFormat;
import com.cronquery.service.model.QueryRequest;
import com.cronquery.service.model.QueryResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for CronQueryServiceImpl.
 * Tests business logic, exception translation, and response transformation.
 */
@ExtendWith(MockitoExtension.class)
class CronQueryServiceImplTest {

    @Mock
    private CrontabLoader crontabLoader;

    @Mock
    private GroovyJarAdapter groovyJarAdapter;

    @Mock
    private Counter groovyJarInvocationCounter;

    @Mock
    private Timer groovyJarExecutionTimer;

    @Mock
    private Counter errorCounter;

    private CronQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CronQueryServiceImpl(
            crontabLoader,
            groovyJarAdapter,
            groovyJarInvocationCounter,
            groovyJarExecutionTimer,
            errorCounter
        );
        
        // Mock timer to execute the supplier immediately (lenient for tests that don't use it)
        lenient().when(groovyJarExecutionTimer.record(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    void testExecuteQuery_WithNaturalLanguageQuery_ReturnsMatchingJobs() {
        // Arrange
        QueryRequest request = new QueryRequest();
        request.setQuery("jobs on Saturday");
        request.setFormat(OutputFormat.JSON);
        
        String crontabContent = "0 8 * * 6 /usr/bin/backup.sh";
        List<CronJob> mockJobs = createMockJobs();
        
        when(crontabLoader.loadCrontabData()).thenReturn(crontabContent);
        when(crontabLoader.getActiveSources()).thenReturn(Arrays.asList("/etc/crontab"));
        when(groovyJarAdapter.queryJobs(any(), anyString())).thenReturn(mockJobs);

        // Act
        QueryResponse response = service.executeQuery(request);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertEquals("jobs on Saturday", response.getQuery());
        assertEquals(1, response.getSources().size());
        assertTrue(response.getExecutionTimeMs() >= 0);
        
        verify(crontabLoader).loadCrontabData();
        verify(groovyJarAdapter).queryJobs(any(), eq(crontabContent));
        verify(groovyJarInvocationCounter).increment();
    }

    @Test
    void testExecuteQuery_WithStructuredQuery_ReturnsMatchingJobs() {
        // Arrange
        QueryRequest request = new QueryRequest();
        request.setDay("Monday");
        request.setTime("08:00");
        request.setFormat(OutputFormat.JSON);
        
        String crontabContent = "0 8 * * 1 /usr/bin/backup.sh";
        List<CronJob> mockJobs = createMockJobs();
        
        when(crontabLoader.loadCrontabData()).thenReturn(crontabContent);
        when(crontabLoader.getActiveSources()).thenReturn(Arrays.asList("/etc/crontab"));
        when(groovyJarAdapter.queryJobs(any(), anyString())).thenReturn(mockJobs);

        // Act
        QueryResponse response = service.executeQuery(request);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertTrue(response.getQuery().contains("day=Monday"));
        assertTrue(response.getQuery().contains("time=08:00"));
        
        verify(groovyJarAdapter).queryJobs(any(), eq(crontabContent));
    }

    @Test
    void testExecuteQuery_WithNullRequest_ThrowsInvalidQueryException() {
        // Act & Assert
        assertThrows(InvalidQueryException.class, () -> {
            service.executeQuery(null);
        });
        
        verify(errorCounter, never()).increment();
    }

    @Test
    void testExecuteQuery_WithNoQueryParameters_ThrowsInvalidQueryException() {
        // Arrange
        QueryRequest request = new QueryRequest();
        request.setFormat(OutputFormat.JSON);

        // Act & Assert
        assertThrows(InvalidQueryException.class, () -> {
            service.executeQuery(request);
        });
    }

    @Test
    void testExecuteQuery_WithCrontabLoadFailure_ThrowsCrontabLoadException() {
        // Arrange
        QueryRequest request = new QueryRequest();
        request.setQuery("jobs on Saturday");
        
        when(crontabLoader.loadCrontabData()).thenThrow(new CrontabLoadException("Failed to load"));

        // Act & Assert
        assertThrows(CrontabLoadException.class, () -> {
            service.executeQuery(request);
        });
        
        verify(errorCounter).increment();
    }

    @Test
    void testExecuteQuery_WithGroovyJarFailure_ThrowsGroovyJarException() {
        // Arrange
        QueryRequest request = new QueryRequest();
        request.setQuery("jobs on Saturday");
        
        String crontabContent = "0 8 * * 6 /usr/bin/backup.sh";
        
        when(crontabLoader.loadCrontabData()).thenReturn(crontabContent);
        when(groovyJarAdapter.queryJobs(any(), anyString())).thenThrow(new GroovyJarException("Query failed"));

        // Act & Assert
        assertThrows(GroovyJarException.class, () -> {
            service.executeQuery(request);
        });
        
        verify(errorCounter).increment();
    }

    @Test
    void testGetAllJobs_ReturnsAllJobs() {
        // Arrange
        String crontabContent = "0 8 * * * /usr/bin/backup.sh\n0 12 * * * /usr/bin/cleanup.sh";
        List<CronJob> mockJobs = createMockJobs();
        
        when(crontabLoader.loadCrontabData()).thenReturn(crontabContent);
        when(crontabLoader.getActiveSources()).thenReturn(Arrays.asList("/etc/crontab"));
        when(groovyJarAdapter.loadAllJobs(anyString())).thenReturn(mockJobs);

        // Act
        List<CronJob> jobs = service.getAllJobs();

        // Assert
        assertNotNull(jobs);
        assertEquals(2, jobs.size());
        
        verify(crontabLoader).loadCrontabData();
        verify(groovyJarAdapter).loadAllJobs(crontabContent);
        verify(groovyJarInvocationCounter).increment();
    }

    @Test
    void testGetAllJobs_WithCrontabLoadFailure_ThrowsCrontabLoadException() {
        // Arrange
        when(crontabLoader.loadCrontabData()).thenThrow(new CrontabLoadException("Failed to load"));

        // Act & Assert
        assertThrows(CrontabLoadException.class, () -> {
            service.getAllJobs();
        });
        
        verify(errorCounter).increment();
    }

    @Test
    void testCheckHealth_WithAllComponentsHealthy_ReturnsUpStatus() {
        // Arrange
        when(crontabLoader.loadCrontabData()).thenReturn("0 8 * * * /usr/bin/backup.sh");
        when(crontabLoader.getActiveSources()).thenReturn(Arrays.asList("/etc/crontab"));
        when(crontabLoader.getJobCount()).thenReturn(5);
        when(groovyJarAdapter.validateCronExpression(anyString())).thenReturn(true);

        // Act
        HealthStatus status = service.checkHealth();

        // Assert
        assertNotNull(status);
        assertEquals(HealthStatus.Status.UP, status.getStatus());
        assertEquals(1, status.getAvailableSources().size());
        assertEquals("UP", status.getDetails().get("crontabLoader"));
        assertEquals("UP", status.getDetails().get("groovyJar"));
    }

    @Test
    void testCheckHealth_WithNoActiveSources_ReturnsDownStatus() {
        // Arrange
        when(crontabLoader.loadCrontabData()).thenReturn("");
        when(crontabLoader.getActiveSources()).thenReturn(Arrays.asList());
        when(crontabLoader.getJobCount()).thenReturn(0);

        // Act
        HealthStatus status = service.checkHealth();

        // Assert
        assertNotNull(status);
        assertEquals(HealthStatus.Status.DOWN, status.getStatus());
        assertTrue(status.getAvailableSources().isEmpty());
    }

    @Test
    void testCheckHealth_WithGroovyJarFailure_ReturnsDegradedStatus() {
        // Arrange
        when(crontabLoader.loadCrontabData()).thenReturn("0 8 * * * /usr/bin/backup.sh");
        when(crontabLoader.getActiveSources()).thenReturn(Arrays.asList("/etc/crontab"));
        when(crontabLoader.getJobCount()).thenReturn(5);
        when(groovyJarAdapter.validateCronExpression(anyString())).thenReturn(false);

        // Act
        HealthStatus status = service.checkHealth();

        // Assert
        assertNotNull(status);
        assertEquals(HealthStatus.Status.DEGRADED, status.getStatus());
        assertEquals("DOWN", status.getDetails().get("groovyJar"));
    }

    @Test
    void testExecuteQuery_WithTimeRangeQuery_BuildsCorrectQueryString() {
        // Arrange
        QueryRequest request = new QueryRequest();
        request.setTimeRange("08:00-17:00");
        request.setFormat(OutputFormat.JSON);
        
        String crontabContent = "0 8 * * * /usr/bin/backup.sh";
        List<CronJob> mockJobs = createMockJobs();
        
        when(crontabLoader.loadCrontabData()).thenReturn(crontabContent);
        when(crontabLoader.getActiveSources()).thenReturn(Arrays.asList("/etc/crontab"));
        when(groovyJarAdapter.queryJobs(any(), anyString())).thenReturn(mockJobs);

        // Act
        QueryResponse response = service.executeQuery(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.getQuery().contains("timeRange=08:00-17:00"));
    }

    @Test
    void testExecuteQuery_WithMultipleStructuredParameters_BuildsCorrectQueryString() {
        // Arrange
        QueryRequest request = new QueryRequest();
        request.setDay("Monday");
        request.setTime("08:00");
        request.setTimeRange("08:00-17:00");
        request.setFormat(OutputFormat.JSON);
        
        String crontabContent = "0 8 * * 1 /usr/bin/backup.sh";
        List<CronJob> mockJobs = createMockJobs();
        
        when(crontabLoader.loadCrontabData()).thenReturn(crontabContent);
        when(crontabLoader.getActiveSources()).thenReturn(Arrays.asList("/etc/crontab"));
        when(groovyJarAdapter.queryJobs(any(), anyString())).thenReturn(mockJobs);

        // Act
        QueryResponse response = service.executeQuery(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.getQuery().contains("day=Monday"));
        assertTrue(response.getQuery().contains("time=08:00"));
        assertTrue(response.getQuery().contains("timeRange=08:00-17:00"));
    }

    /**
     * Helper method to create mock CronJob list for testing.
     */
    private List<CronJob> createMockJobs() {
        CronJob job1 = new CronJob();
        job1.setSchedule("0 8 * * 6");
        job1.setCommand("/usr/bin/backup.sh");
        job1.setSource("/etc/crontab");
        job1.setUser("root");
        
        CronJob job2 = new CronJob();
        job2.setSchedule("0 12 * * 6");
        job2.setCommand("/usr/bin/cleanup.sh");
        job2.setSource("/etc/crontab");
        job2.setUser("root");
        
        return Arrays.asList(job1, job2);
    }
}
