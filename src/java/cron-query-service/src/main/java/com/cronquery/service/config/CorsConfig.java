package com.cronquery.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration for the CronQuery service.
 * Allows configurable allowed origins for web client access.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cronquery.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${cronquery.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${cronquery.cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${cronquery.cors.allow-credentials:false}")
    private boolean allowCredentials;

    @Value("${cronquery.cors.max-age:3600}")
    private long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = parseCommaSeparatedValues(allowedOrigins);
        List<String> methods = parseCommaSeparatedValues(allowedMethods);
        List<String> headers = parseCommaSeparatedValues(allowedHeaders);

        registry.addMapping("/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods(methods.toArray(new String[0]))
                .allowedHeaders(headers.toArray(new String[0]))
                .allowCredentials(allowCredentials)
                .maxAge(maxAge);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        List<String> origins = parseCommaSeparatedValues(allowedOrigins);
        List<String> methods = parseCommaSeparatedValues(allowedMethods);
        List<String> headers = parseCommaSeparatedValues(allowedHeaders);

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(methods);
        configuration.setAllowedHeaders(headers);
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    /**
     * Parse comma-separated values from configuration string.
     * Handles both single values and comma-separated lists.
     */
    private List<String> parseCommaSeparatedValues(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of("*");
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
