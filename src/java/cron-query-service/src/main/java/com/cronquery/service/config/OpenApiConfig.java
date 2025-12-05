package com.cronquery.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.ExternalDocumentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for the Cron Query Service API.
 * 
 * Provides comprehensive API documentation with metadata, contact information,
 * and server configuration for the REST endpoints.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${info.app.version}")
    private String applicationVersion;

    @Value("${info.app.description}")
    private String applicationDescription;

    /**
     * Configure OpenAPI documentation with API metadata.
     * 
     * @return OpenAPI configuration object
     */
    @Bean
    public OpenAPI cronQueryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cron Query Service API")
                        .description("""
                                REST API for querying and analyzing cron job schedules using natural language or structured parameters.
                                
                                ## Features
                                - **Natural Language Queries**: Query jobs using phrases like "jobs on weekends" or "what runs at 8 AM"
                                - **Structured Queries**: Filter by specific day, time, or time range
                                - **Multiple Output Formats**: Get results in JSON, CSV, or YAML format
                                - **Real-time Analysis**: Analyze cron schedules from system crontabs or custom sources
                                - **Next Execution Times**: See when jobs will run next
                                
                                ## Query Examples
                                - `?query=jobs on Saturday` - Find all jobs that run on Saturday
                                - `?query=what runs at 8 AM` - Find jobs scheduled for 8:00 AM
                                - `?day=Monday&time=09:00` - Find jobs on Monday at 9:00 AM
                                - `?timeRange=08:00-17:00` - Find jobs running during business hours
                                - `?query=weekend jobs&format=csv` - Get weekend jobs in CSV format
                                
                                ## Data Sources
                                The service can load cron jobs from multiple sources:
                                - User crontab (`crontab -l`)
                                - System crontab (`/etc/crontab`)
                                - Cron directories (`/etc/cron.d/*`)
                                - Test files (for development and testing)
                                """)
                        .version(applicationVersion)
                        .contact(new Contact()
                                .name("Cron Query Project")
                                .url("https://github.com/tbaldarelli/cron-query")
                                .email("support@cronquery.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("Cron Query Documentation")
                        .url("https://github.com/tbaldarelli/cron-query/blob/main/README.md"))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Production server (configure as needed)")
                ));
    }
}
