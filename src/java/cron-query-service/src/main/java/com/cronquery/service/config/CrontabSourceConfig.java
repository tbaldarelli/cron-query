package com.cronquery.service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for crontab sources.
 * Maps to the 'cronquery.crontab' section in application.yml.
 * Supports environment variable overrides for paths and enabled flags.
 */
@Configuration
@ConfigurationProperties(prefix = "cronquery.crontab")
public class CrontabSourceConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(CrontabSourceConfig.class);
    
    private List<Source> sources = new ArrayList<>();
    
    public List<Source> getSources() {
        return sources;
    }
    
    public void setSources(List<Source> sources) {
        this.sources = sources;
    }
    
    /**
     * Log active sources at startup for visibility.
     */
    @PostConstruct
    public void logConfiguration() {
        logger.info("Crontab source configuration:");
        for (Source source : sources) {
            if (source.isEnabled()) {
                String sourceInfo = String.format("  - Type: %s, Path: %s, Fallback: %s", 
                    source.getType(), 
                    source.getPath() != null ? source.getPath() : "N/A",
                    source.isFallback());
                logger.info(sourceInfo);
            } else {
                logger.debug("  - Type: {} (DISABLED)", source.getType());
            }
        }
    }
    
    /**
     * Represents a single crontab source configuration.
     */
    public static class Source {
        private String type;
        private String path;
        private boolean enabled = true;
        private boolean fallback = false;
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public boolean isFallback() {
            return fallback;
        }
        
        public void setFallback(boolean fallback) {
            this.fallback = fallback;
        }
        
        @Override
        public String toString() {
            return "Source{" +
                "type='" + type + '\'' +
                ", path='" + path + '\'' +
                ", enabled=" + enabled +
                ", fallback=" + fallback +
                '}';
        }
    }
}
