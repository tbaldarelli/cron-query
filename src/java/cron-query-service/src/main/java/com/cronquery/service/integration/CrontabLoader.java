package com.cronquery.service.integration;

import java.util.List;

/**
 * Interface for loading crontab data from various sources.
 * Implementations should support multiple crontab sources with fallback logic.
 */
public interface CrontabLoader {
    
    /**
     * Load crontab data from all configured sources.
     * Attempts to load from multiple sources and combines the results.
     * Falls back to test file if system sources are unavailable.
     * 
     * @return Combined crontab content from all available sources
     * @throws com.cronquery.service.exception.CrontabLoadException if no sources are available
     */
    String loadCrontabData();
    
    /**
     * Get the list of active crontab sources that were successfully loaded.
     * 
     * @return List of source identifiers (e.g., "user_crontab", "system_crontab", "test_file")
     */
    List<String> getActiveSources();
    
    /**
     * Get the total number of cron jobs loaded from all sources.
     * 
     * @return Count of cron job entries
     */
    int getJobCount();
}
