package com.cronquery.service.integration;

import com.cronquery.service.config.CrontabSourceConfig;
import com.cronquery.service.exception.CrontabLoadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CrontabLoaderImpl.
 * Tests crontab loading with fallback logic and configuration handling.
 */
class CrontabLoaderImplTest {

    @TempDir
    Path tempDir;

    private CrontabSourceConfig config;
    private CrontabLoaderImpl loader;

    @BeforeEach
    void setUp() {
        config = new CrontabSourceConfig();
        config.setSources(new ArrayList<>());
    }

    @Test
    void testLoadCrontabData_FromFile_Success() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("test_crontab.txt");
        String crontabContent = "0 8 * * * /usr/bin/backup.sh\n0 12 * * * /usr/bin/cleanup.sh\n";
        Files.writeString(testFile, crontabContent);

        CrontabSourceConfig.Source fileSource = new CrontabSourceConfig.Source();
        fileSource.setType("file");
        fileSource.setPath(testFile.toString());
        fileSource.setEnabled(true);
        fileSource.setFallback(false);
        config.getSources().add(fileSource);

        loader = new CrontabLoaderImpl(config);

        // Act
        String result = loader.loadCrontabData();

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("backup.sh"));
        assertTrue(result.contains("cleanup.sh"));
        assertEquals(1, loader.getActiveSources().size());
        assertEquals("file", loader.getActiveSources().get(0));
        assertEquals(2, loader.getJobCount());
    }

    @Test
    void testLoadCrontabData_WithFallback_UsesFallbackWhenPrimaryFails() throws IOException {
        // Arrange
        // Add a non-existent system source
        CrontabSourceConfig.Source systemSource = new CrontabSourceConfig.Source();
        systemSource.setType("system");
        systemSource.setPath("/nonexistent/crontab");
        systemSource.setEnabled(true);
        systemSource.setFallback(false);
        config.getSources().add(systemSource);

        // Add a fallback file source
        Path testFile = tempDir.resolve("fallback_crontab.txt");
        String crontabContent = "0 8 * * * /usr/bin/test.sh\n";
        Files.writeString(testFile, crontabContent);

        CrontabSourceConfig.Source fallbackSource = new CrontabSourceConfig.Source();
        fallbackSource.setType("file");
        fallbackSource.setPath(testFile.toString());
        fallbackSource.setEnabled(true);
        fallbackSource.setFallback(true);
        config.getSources().add(fallbackSource);

        loader = new CrontabLoaderImpl(config);

        // Act
        String result = loader.loadCrontabData();

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("test.sh"));
        assertEquals(1, loader.getActiveSources().size());
        assertTrue(loader.getActiveSources().get(0).contains("fallback"));
        assertEquals(1, loader.getJobCount());
    }

    @Test
    void testLoadCrontabData_NoSourcesAvailable_ThrowsException() {
        // Arrange
        CrontabSourceConfig.Source systemSource = new CrontabSourceConfig.Source();
        systemSource.setType("system");
        systemSource.setPath("/nonexistent/crontab");
        systemSource.setEnabled(true);
        systemSource.setFallback(false);
        config.getSources().add(systemSource);

        loader = new CrontabLoaderImpl(config);

        // Act & Assert
        assertThrows(CrontabLoadException.class, () -> {
            loader.loadCrontabData();
        });
    }

    @Test
    void testLoadCrontabData_DisabledSource_IsSkipped() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("test_crontab.txt");
        String crontabContent = "0 8 * * * /usr/bin/backup.sh\n";
        Files.writeString(testFile, crontabContent);

        CrontabSourceConfig.Source disabledSource = new CrontabSourceConfig.Source();
        disabledSource.setType("file");
        disabledSource.setPath(testFile.toString());
        disabledSource.setEnabled(false);
        disabledSource.setFallback(false);
        config.getSources().add(disabledSource);

        // Add a fallback that should be used
        Path fallbackFile = tempDir.resolve("fallback.txt");
        Files.writeString(fallbackFile, "0 12 * * * /usr/bin/test.sh\n");

        CrontabSourceConfig.Source fallbackSource = new CrontabSourceConfig.Source();
        fallbackSource.setType("file");
        fallbackSource.setPath(fallbackFile.toString());
        fallbackSource.setEnabled(true);
        fallbackSource.setFallback(true);
        config.getSources().add(fallbackSource);

        loader = new CrontabLoaderImpl(config);

        // Act
        String result = loader.loadCrontabData();

        // Assert
        assertNotNull(result);
        assertFalse(result.contains("backup.sh"));
        assertTrue(result.contains("test.sh"));
    }

    @Test
    void testLoadCrontabData_EmptyFile_IsSkipped() throws IOException {
        // Arrange
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "");

        CrontabSourceConfig.Source emptySource = new CrontabSourceConfig.Source();
        emptySource.setType("file");
        emptySource.setPath(emptyFile.toString());
        emptySource.setEnabled(true);
        emptySource.setFallback(false);
        config.getSources().add(emptySource);

        // Add a fallback
        Path fallbackFile = tempDir.resolve("fallback.txt");
        Files.writeString(fallbackFile, "0 8 * * * /usr/bin/test.sh\n");

        CrontabSourceConfig.Source fallbackSource = new CrontabSourceConfig.Source();
        fallbackSource.setType("file");
        fallbackSource.setPath(fallbackFile.toString());
        fallbackSource.setEnabled(true);
        fallbackSource.setFallback(true);
        config.getSources().add(fallbackSource);

        loader = new CrontabLoaderImpl(config);

        // Act
        String result = loader.loadCrontabData();

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("test.sh"));
    }

    @Test
    void testJobCount_CountsOnlyValidCronLines() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("test_crontab.txt");
        String crontabContent = """
            # This is a comment
            0 8 * * * /usr/bin/backup.sh
            
            # Another comment
            0 12 * * * /usr/bin/cleanup.sh
            SHELL=/bin/bash
            0 18 * * * /usr/bin/evening.sh
            """;
        Files.writeString(testFile, crontabContent);

        CrontabSourceConfig.Source fileSource = new CrontabSourceConfig.Source();
        fileSource.setType("file");
        fileSource.setPath(testFile.toString());
        fileSource.setEnabled(true);
        fileSource.setFallback(false);
        config.getSources().add(fileSource);

        loader = new CrontabLoaderImpl(config);

        // Act
        loader.loadCrontabData();

        // Assert
        assertEquals(3, loader.getJobCount());
    }

    @Test
    void testLoadCrontabData_MultipleSources_CombinesContent() throws IOException {
        // Arrange
        Path file1 = tempDir.resolve("crontab1.txt");
        Files.writeString(file1, "0 8 * * * /usr/bin/backup.sh\n");

        Path file2 = tempDir.resolve("crontab2.txt");
        Files.writeString(file2, "0 12 * * * /usr/bin/cleanup.sh\n");

        CrontabSourceConfig.Source source1 = new CrontabSourceConfig.Source();
        source1.setType("file");
        source1.setPath(file1.toString());
        source1.setEnabled(true);
        source1.setFallback(false);
        config.getSources().add(source1);

        CrontabSourceConfig.Source source2 = new CrontabSourceConfig.Source();
        source2.setType("file");
        source2.setPath(file2.toString());
        source2.setEnabled(true);
        source2.setFallback(false);
        config.getSources().add(source2);

        loader = new CrontabLoaderImpl(config);

        // Act
        String result = loader.loadCrontabData();

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("backup.sh"));
        assertTrue(result.contains("cleanup.sh"));
        assertEquals(2, loader.getActiveSources().size());
        assertEquals(2, loader.getJobCount());
    }

    @Test
    void testGetActiveSources_ReturnsImmutableCopy() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("test_crontab.txt");
        Files.writeString(testFile, "0 8 * * * /usr/bin/backup.sh\n");

        CrontabSourceConfig.Source fileSource = new CrontabSourceConfig.Source();
        fileSource.setType("file");
        fileSource.setPath(testFile.toString());
        fileSource.setEnabled(true);
        fileSource.setFallback(false);
        config.getSources().add(fileSource);

        loader = new CrontabLoaderImpl(config);
        loader.loadCrontabData();

        // Act
        List<String> sources1 = loader.getActiveSources();
        List<String> sources2 = loader.getActiveSources();

        // Assert
        assertNotSame(sources1, sources2);
        assertEquals(sources1, sources2);
    }
}
