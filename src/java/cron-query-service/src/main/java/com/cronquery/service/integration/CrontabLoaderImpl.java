package com.cronquery.service.integration;

import com.cronquery.service.config.CrontabSourceConfig;
import com.cronquery.service.exception.CrontabLoadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of CrontabLoader that supports loading from multiple sources:
 * - User crontab (via crontab -l command)
 * - System crontab (/etc/crontab)
 * - Cron.d directories (/etc/cron.d/*)
 * - Test file (fallback for development)
 */
@Component
public class CrontabLoaderImpl implements CrontabLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(CrontabLoaderImpl.class);
    
    private final CrontabSourceConfig config;
    private final List<String> activeSources;
    private int jobCount;
    
    public CrontabLoaderImpl(CrontabSourceConfig config) {
        this.config = config;
        this.activeSources = new ArrayList<>();
        this.jobCount = 0;
    }
    
    @Override
    public String loadCrontabData() {
        activeSources.clear();
        jobCount = 0;
        
        StringBuilder combinedCrontab = new StringBuilder();
        boolean anySourceLoaded = false;
        
        // Try each configured source
        for (CrontabSourceConfig.Source source : config.getSources()) {
            if (!source.isEnabled()) {
                logger.debug("Skipping disabled source: {}", source.getType());
                continue;
            }
            
            // Skip fallback sources initially
            if (source.isFallback()) {
                continue;
            }
            
            try {
                String content = loadFromSource(source);
                if (content != null && !content.trim().isEmpty()) {
                    combinedCrontab.append(content);
                    if (!content.endsWith("\n")) {
                        combinedCrontab.append("\n");
                    }
                    activeSources.add(source.getType());
                    anySourceLoaded = true;
                    logger.info("Successfully loaded crontab from source: {}", source.getType());
                }
            } catch (Exception e) {
                logger.warn("Failed to load crontab from source {}: {}", source.getType(), e.getMessage());
            }
        }
        
        // If no sources loaded, try fallback sources
        if (!anySourceLoaded) {
            logger.info("No system crontab sources available, attempting fallback sources");
            for (CrontabSourceConfig.Source source : config.getSources()) {
                if (source.isEnabled() && source.isFallback()) {
                    try {
                        String content = loadFromSource(source);
                        if (content != null && !content.trim().isEmpty()) {
                            combinedCrontab.append(content);
                            if (!content.endsWith("\n")) {
                                combinedCrontab.append("\n");
                            }
                            activeSources.add(source.getType() + "_fallback");
                            anySourceLoaded = true;
                            logger.info("Successfully loaded crontab from fallback source: {}", source.getType());
                            break; // Only use first fallback
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to load crontab from fallback source {}: {}", source.getType(), e.getMessage());
                    }
                }
            }
        }
        
        if (!anySourceLoaded) {
            throw new CrontabLoadException("No crontab sources available. Unable to load any crontab data.");
        }
        
        // Count jobs (non-empty, non-comment lines)
        String finalContent = combinedCrontab.toString();
        jobCount = countCronJobs(finalContent);
        
        logger.info("Loaded {} cron jobs from {} source(s): {}", jobCount, activeSources.size(), activeSources);
        
        return finalContent;
    }
    
    @Override
    public List<String> getActiveSources() {
        return new ArrayList<>(activeSources);
    }
    
    @Override
    public int getJobCount() {
        return jobCount;
    }
    
    /**
     * Load crontab content from a specific source based on its type.
     */
    private String loadFromSource(CrontabSourceConfig.Source source) throws IOException {
        switch (source.getType().toLowerCase()) {
            case "user":
                return loadUserCrontab();
            case "system":
                return loadSystemCrontab(source.getPath());
            case "directory":
                return loadCronDirectory(source.getPath());
            case "file":
                return loadCrontabFile(source.getPath());
            default:
                logger.warn("Unknown source type: {}", source.getType());
                return null;
        }
    }
    
    /**
     * Load user crontab using 'crontab -l' command.
     */
    private String loadUserCrontab() throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("crontab", "-l");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return output.toString();
            } else {
                logger.debug("crontab -l returned exit code {}", exitCode);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while loading user crontab", e);
        } catch (IOException e) {
            logger.debug("Unable to execute crontab -l: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Load system crontab from /etc/crontab.
     */
    private String loadSystemCrontab(String path) throws IOException {
        Path crontabPath = Paths.get(path != null ? path : "/etc/crontab");
        if (Files.exists(crontabPath) && Files.isReadable(crontabPath)) {
            return Files.readString(crontabPath);
        }
        logger.debug("System crontab not accessible: {}", crontabPath);
        return null;
    }
    
    /**
     * Load all crontab files from a directory (e.g., /etc/cron.d).
     */
    private String loadCronDirectory(String path) throws IOException {
        Path dirPath = Paths.get(path != null ? path : "/etc/cron.d");
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            logger.debug("Cron directory not accessible: {}", dirPath);
            return null;
        }
        
        StringBuilder combined = new StringBuilder();
        try (Stream<Path> files = Files.list(dirPath)) {
            List<Path> cronFiles = files
                .filter(Files::isRegularFile)
                .filter(Files::isReadable)
                .collect(Collectors.toList());
            
            for (Path file : cronFiles) {
                try {
                    String content = Files.readString(file);
                    combined.append("# From: ").append(file.getFileName()).append("\n");
                    combined.append(content);
                    if (!content.endsWith("\n")) {
                        combined.append("\n");
                    }
                } catch (IOException e) {
                    logger.warn("Failed to read cron file {}: {}", file, e.getMessage());
                }
            }
        }
        
        return combined.length() > 0 ? combined.toString() : null;
    }
    
    /**
     * Load crontab from a file (typically for testing).
     */
    private String loadCrontabFile(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        
        // Try as absolute path first
        Path filePath = Paths.get(path);
        if (Files.exists(filePath) && Files.isReadable(filePath)) {
            return Files.readString(filePath);
        }
        
        // Try as classpath resource
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (inputStream != null) {
                return new String(inputStream.readAllBytes());
            }
        }
        
        logger.debug("Crontab file not found: {}", path);
        return null;
    }
    
    /**
     * Count the number of actual cron job entries (non-empty, non-comment lines).
     */
    private int countCronJobs(String crontabContent) {
        if (crontabContent == null || crontabContent.trim().isEmpty()) {
            return 0;
        }
        
        return (int) crontabContent.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .filter(line -> !line.startsWith("#"))
            .filter(line -> line.split("\\s+").length >= 6) // Valid cron line has at least 6 fields
            .count();
    }
}
