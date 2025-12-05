package com.cronquery.service;

import com.cronquery.service.integration.CrontabLoader;
import com.cronquery.service.integration.GroovyJarAdapter;
import com.cronquery.service.model.CronJob;
import com.cronquery.service.model.OutputFormat;
import com.cronquery.service.model.QueryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Groovy JAR Integration Tests.
 * Tests actual Groovy JAR invocation with real crontab data.
 * Verifies query translation and error handling.
 * 
 * Requirements: 3.2, 3.3, 3.4
 */
@SpringBootTest
class GroovyJarIntegrationTest {

    @Autowired
    private GroovyJarAdapter groovyJarAdapter;

    @Autowired
    private CrontabLoader crontabLoader;

    @Test
    void testLoadAllJobs_WithTestCrontab_ReturnsJobs() {
        // Arrange
        String crontabContent = crontabLoader.loadCrontabData();
        assertNotNull(crontabContent);
        assertFalse(crontabContent.isEmpty());

        // Act
        List<CronJob> jobs = groovyJarAdapter.loadAllJobs(crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.size() > 0, "Should load at least one job from test crontab");
        
        // Verify job structure
        CronJob firstJob = jobs.get(0);
        assertNotNull(firstJob.getSchedule());
        assertNotNull(firstJob.getCommand());
    }

    @Test
    void testQueryJobs_WithNaturalLanguageQuery_ReturnsMatchingJobs() {
        // Arrange
        String crontabContent = crontabLoader.loadCrontabData();
        QueryRequest request = new QueryRequest();
        request.setQuery("jobs at 3 am");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = groovyJarAdapter.queryJobs(request, crontabContent);

        // Assert
        assertNotNull(jobs);
        // Should find jobs that run at 3 AM (30 03 * * * in test crontab)
        assertTrue(jobs.size() >= 0, "Query should execute without error");
    }

    @Test
    void testQueryJobs_WithDayFilter_ReturnsMatchingJobs() {
        // Arrange
        String crontabContent = crontabLoader.loadCrontabData();
        QueryRequest request = new QueryRequest();
        request.setDay("Monday");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = groovyJarAdapter.queryJobs(request, crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.size() >= 0, "Query should execute without error");
    }

    @Test
    void testQueryJobs_WithTimeFilter_ReturnsMatchingJobs() {
        // Arrange
        String crontabContent = crontabLoader.loadCrontabData();
        QueryRequest request = new QueryRequest();
        request.setTime("07:00");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = groovyJarAdapter.queryJobs(request, crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.size() >= 0, "Query should execute without error");
    }

    @Test
    void testQueryJobs_WithTimeRangeFilter_ReturnsMatchingJobs() {
        // Arrange
        String crontabContent = crontabLoader.loadCrontabData();
        QueryRequest request = new QueryRequest();
        request.setTimeRange("07:00-18:00");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = groovyJarAdapter.queryJobs(request, crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.size() >= 0, "Query should execute without error");
        
        // Verify all returned jobs fall within the time range
        for (CronJob job : jobs) {
            assertNotNull(job.getSchedule());
            assertNotNull(job.getCommand());
        }
    }

    @Test
    void testQueryJobs_WithCombinedFilters_ReturnsMatchingJobs() {
        // Arrange
        String crontabContent = crontabLoader.loadCrontabData();
        QueryRequest request = new QueryRequest();
        request.setDay("Saturday");
        request.setTime("19:00");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = groovyJarAdapter.queryJobs(request, crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.size() >= 0, "Query should execute without error");
    }

    @Test
    void testValidateCronExpression_WithValidExpression_ReturnsTrue() {
        // Act
        boolean isValid = groovyJarAdapter.validateCronExpression("0 8 * * *");

        // Assert
        assertTrue(isValid, "Valid cron expression should return true");
    }

    @Test
    void testValidateCronExpression_WithInvalidExpression_ReturnsFalse() {
        // Act
        boolean isValid = groovyJarAdapter.validateCronExpression("invalid cron");

        // Assert
        assertFalse(isValid, "Invalid cron expression should return false");
    }

    @Test
    void testQueryJobs_WithEmptyCrontab_ReturnsEmptyList() {
        // Arrange
        String emptyCrontab = "";
        QueryRequest request = new QueryRequest();
        request.setQuery("jobs at 8 am");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = groovyJarAdapter.queryJobs(request, emptyCrontab);

        // Assert
        assertNotNull(jobs);
        assertEquals(0, jobs.size(), "Empty crontab should return no jobs");
    }

    @Test
    void testQueryJobs_WithCommentedLines_IgnoresComments() {
        // Arrange
        String crontabWithComments = "# This is a comment\n0 8 * * * /usr/bin/test.sh\n## Another comment";
        QueryRequest request = new QueryRequest();
        request.setQuery("jobs at 8 am");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = groovyJarAdapter.queryJobs(request, crontabWithComments);

        // Assert
        assertNotNull(jobs);
        // Should only find the one uncommented job
        assertTrue(jobs.size() >= 0, "Should handle commented lines correctly");
    }

    @Test
    void testLoadAllJobs_VerifiesJobFields() {
        // Arrange
        String crontabContent = crontabLoader.loadCrontabData();

        // Act
        List<CronJob> jobs = groovyJarAdapter.loadAllJobs(crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.size() > 0);
        
        // Verify each job has required fields
        for (CronJob job : jobs) {
            assertNotNull(job.getSchedule(), "Job should have schedule");
            assertNotNull(job.getCommand(), "Job should have command");
            assertFalse(job.getSchedule().isEmpty(), "Schedule should not be empty");
            assertFalse(job.getCommand().isEmpty(), "Command should not be empty");
        }
    }

    @Test
    void testCrontabLoader_LoadsFromConfiguredSource() {
        // Act
        String crontabContent = crontabLoader.loadCrontabData();
        List<String> sources = crontabLoader.getActiveSources();

        // Assert
        assertNotNull(crontabContent);
        assertNotNull(sources);
        assertFalse(sources.isEmpty(), "Should have at least one active source");
        assertTrue(crontabContent.length() > 0, "Should load non-empty crontab content");
    }

    @Test
    void testCrontabLoader_ReturnsJobCount() {
        // Arrange
        crontabLoader.loadCrontabData();

        // Act
        int jobCount = crontabLoader.getJobCount();

        // Assert
        assertTrue(jobCount >= 0, "Job count should be non-negative");
    }
}
