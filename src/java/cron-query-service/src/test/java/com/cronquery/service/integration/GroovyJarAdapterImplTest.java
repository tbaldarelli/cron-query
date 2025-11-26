package com.cronquery.service.integration;

import com.cronquery.service.exception.GroovyJarException;
import com.cronquery.service.exception.InvalidQueryException;
import com.cronquery.service.model.CronJob;
import com.cronquery.service.model.OutputFormat;
import com.cronquery.service.model.QueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GroovyJarAdapterImpl.
 * Tests Groovy JAR integration and query translation.
 */
class GroovyJarAdapterImplTest {

    private GroovyJarAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        adapter = new GroovyJarAdapterImpl();
    }

    @Test
    void testLoadAllJobs_WithValidCrontab_ReturnsJobs() {
        // Arrange
        String crontabContent = """
            0 8 * * * /usr/bin/backup.sh
            0 12 * * * /usr/bin/cleanup.sh
            30 18 * * 1-5 /usr/bin/weekday.sh
            """;

        // Act
        List<CronJob> jobs = adapter.loadAllJobs(crontabContent);

        // Assert
        assertNotNull(jobs);
        assertEquals(3, jobs.size());
        
        CronJob firstJob = jobs.get(0);
        assertNotNull(firstJob.getSchedule());
        assertNotNull(firstJob.getCommand());
        assertTrue(firstJob.getCommand().contains("backup.sh"));
    }

    @Test
    void testLoadAllJobs_WithEmptyCrontab_ReturnsEmptyList() {
        // Arrange
        String crontabContent = "";

        // Act
        List<CronJob> jobs = adapter.loadAllJobs(crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.isEmpty());
    }

    @Test
    void testLoadAllJobs_WithCommentsOnly_ReturnsEmptyList() {
        // Arrange
        String crontabContent = """
            # This is a comment
            # Another comment
            """;

        // Act
        List<CronJob> jobs = adapter.loadAllJobs(crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.isEmpty());
    }

    @Test
    void testQueryJobs_WithNaturalLanguageQuery_ReturnsMatchingJobs() {
        // Arrange
        String crontabContent = """
            0 8 * * 6 /usr/bin/saturday_backup.sh
            0 12 * * 0 /usr/bin/sunday_cleanup.sh
            0 9 * * 1-5 /usr/bin/weekday_job.sh
            """;

        QueryRequest request = new QueryRequest();
        request.setQuery("jobs on weekends");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = adapter.queryJobs(request, crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.size() >= 1);
        // Should match Saturday and Sunday jobs
        assertTrue(jobs.stream().anyMatch(j -> j.getCommand().contains("saturday") || j.getCommand().contains("sunday")));
    }

    @Test
    void testQueryJobs_WithStructuredDayQuery_ReturnsMatchingJobs() {
        // Arrange
        String crontabContent = """
            0 8 * * 1 /usr/bin/monday_job.sh
            0 12 * * 2 /usr/bin/tuesday_job.sh
            0 9 * * 1-5 /usr/bin/weekday_job.sh
            """;

        QueryRequest request = new QueryRequest();
        request.setDay("Monday");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = adapter.queryJobs(request, crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.size() >= 1);
        // Should match Monday jobs
        assertTrue(jobs.stream().anyMatch(j -> j.getCommand().contains("monday") || j.getCommand().contains("weekday")));
    }

    @Test
    void testQueryJobs_WithStructuredTimeQuery_ReturnsMatchingJobs() {
        // Arrange
        String crontabContent = """
            0 8 * * * /usr/bin/morning_job.sh
            0 12 * * * /usr/bin/noon_job.sh
            0 18 * * * /usr/bin/evening_job.sh
            """;

        QueryRequest request = new QueryRequest();
        request.setTime("08:00");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = adapter.queryJobs(request, crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.size() >= 1);
        // Should match 8 AM job
        assertTrue(jobs.stream().anyMatch(j -> j.getCommand().contains("morning")));
    }

    @Test
    void testQueryJobs_WithInvalidQuery_ThrowsInvalidQueryException() {
        // Arrange
        String crontabContent = "0 8 * * * /usr/bin/backup.sh";

        QueryRequest request = new QueryRequest();
        request.setQuery("invalid query xyz123");
        request.setFormat(OutputFormat.JSON);

        // Act & Assert
        assertThrows(InvalidQueryException.class, () -> {
            adapter.queryJobs(request, crontabContent);
        });
    }

    @Test
    void testQueryJobs_WithNoMatchingJobs_ReturnsEmptyList() {
        // Arrange
        String crontabContent = """
            0 8 * * 1-5 /usr/bin/weekday_job.sh
            """;

        QueryRequest request = new QueryRequest();
        request.setQuery("jobs on Saturday");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = adapter.queryJobs(request, crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.isEmpty());
    }

    @Test
    void testValidateCronExpression_WithValidExpression_ReturnsTrue() {
        // Arrange
        String validExpression = "0 8 * * *";

        // Act
        boolean isValid = adapter.validateCronExpression(validExpression);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void testValidateCronExpression_WithInvalidExpression_ReturnsFalse() {
        // Arrange
        String invalidExpression = "invalid cron";

        // Act
        boolean isValid = adapter.validateCronExpression(invalidExpression);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void testValidateCronExpression_WithComplexExpression_ReturnsTrue() {
        // Arrange
        String complexExpression = "*/15 8-17 * * 1-5";

        // Act
        boolean isValid = adapter.validateCronExpression(complexExpression);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void testConvertToCronJob_IncludesNextRuns() {
        // Arrange
        String crontabContent = "0 8 * * * /usr/bin/backup.sh";

        // Act
        List<CronJob> jobs = adapter.loadAllJobs(crontabContent);

        // Assert
        assertNotNull(jobs);
        assertFalse(jobs.isEmpty());
        
        CronJob job = jobs.get(0);
        assertNotNull(job.getNextRuns());
        // Should have calculated next run times
        assertTrue(job.getNextRuns().size() > 0);
    }

    @Test
    void testConvertToCronJob_IncludesDescription() {
        // Arrange
        String crontabContent = "0 8 * * * /usr/bin/backup.sh";

        // Act
        List<CronJob> jobs = adapter.loadAllJobs(crontabContent);

        // Assert
        assertNotNull(jobs);
        assertFalse(jobs.isEmpty());
        
        CronJob job = jobs.get(0);
        assertNotNull(job.getDescription());
        assertFalse(job.getDescription().isEmpty());
    }

    @Test
    void testQueryJobs_WithTimeRangeQuery_ReturnsMatchingJobs() {
        // Arrange
        String crontabContent = """
            0 8 * * * /usr/bin/morning_job.sh
            0 12 * * * /usr/bin/noon_job.sh
            0 18 * * * /usr/bin/evening_job.sh
            0 22 * * * /usr/bin/night_job.sh
            """;

        QueryRequest request = new QueryRequest();
        request.setTimeRange("08:00-17:00");
        request.setFormat(OutputFormat.JSON);

        // Act
        List<CronJob> jobs = adapter.queryJobs(request, crontabContent);

        // Assert
        assertNotNull(jobs);
        assertTrue(jobs.size() >= 2);
        // Should match jobs between 8 AM and 5 PM
        assertTrue(jobs.stream().anyMatch(j -> j.getCommand().contains("morning") || j.getCommand().contains("noon")));
        // Should not match night job
        assertFalse(jobs.stream().anyMatch(j -> j.getCommand().contains("night")));
    }

    @Test
    void testLoadAllJobs_WithMalformedLines_SkipsInvalidLines() {
        // Arrange
        String crontabContent = """
            0 8 * * * /usr/bin/valid_job.sh
            invalid line here
            0 12 * * * /usr/bin/another_valid_job.sh
            """;

        // Act
        List<CronJob> jobs = adapter.loadAllJobs(crontabContent);

        // Assert
        assertNotNull(jobs);
        assertEquals(2, jobs.size());
        assertTrue(jobs.stream().allMatch(j -> j.getCommand().contains("valid")));
    }
}
