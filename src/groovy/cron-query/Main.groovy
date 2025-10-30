package com.cronquery

import groovy.transform.CompileStatic
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import org.fusesource.jansi.AnsiConsole

import java.util.concurrent.Callable

/**
 * Main CLI entry point for cron-query tool.
 * 
 * A command-line tool for querying and analyzing crontab schedules using natural language queries.
 */
@Command(
    name = "cron-query",
    version = "1.1.1",
    description = "Query crontab schedules with natural language",
    mixinStandardHelpOptions = true,
    footer = """
Examples:
  Basic queries:
    cron-query "which jobs run on Saturday"
    cron-query "which jobs run at 8 AM"
    cron-query "which jobs run on weekdays"
  
  Relative date queries:
    cron-query "which jobs run this Saturday"
    cron-query "which jobs run next Monday"
    cron-query "which jobs run coming weekend"
  
  Time range queries:
    cron-query "which jobs run after 10 AM"
    cron-query "which jobs run before 5 PM"
    cron-query "which jobs run between 9 AM and 5 PM"
  
  Combined queries:
    cron-query "which jobs run this Saturday after 10 AM"
    cron-query "which jobs run weekends before 5 PM"
    cron-query "which jobs run Monday between 9 AM and 5 PM"
  
  File analysis:
    cron-query --file /path/to/crontab "which jobs run on Saturday"
  
  Output formats:
    cron-query --format json "which jobs run today"
    cron-query --format table "which jobs run after 6 PM"
"""
)
@CompileStatic
class Main implements Callable<Integer> {
    
    @Parameters(
        index = "0",
        arity = "0..1",
        description = """Natural language query about cron job schedules. 
Supports basic queries ("Saturday", "8 AM"), 
relative dates ("this Saturday", "next Monday"), 
time ranges ("after 10 AM", "between 9 AM and 5 PM"), 
and combined queries ("this Saturday after 10 AM")."""
    )
    String query
    
    @Option(
        names = ['--format', '-f'],
        description = 'Output format (default: list). Available: list, table, json, yaml, compact',
        defaultValue = 'list'
    )
    String format
    
    @Option(
        names = ['--source', '-s'],
        description = 'Source of crontabs (default: user). Options: user, system, all',
        defaultValue = 'user'
    )
    String source
    
    @Option(
        names = ['--file'],
        description = 'Path to a specific crontab file to query',
        paramLabel = 'PATH'
    )
    String file
    
    @Option(
        names = ['--verbose', '-v'],
        description = 'Enable verbose logging'
    )
    boolean verbose
    
    @Option(
        names = ['--no-color'],
        description = 'Disable colored output'
    )
    boolean noColor
    
    @Option(
        names = ['--template', '-t'],
        description = 'Custom output template or predefined template name (compact, detailed, summary, verbose)',
        paramLabel = 'TEMPLATE'
    )
    String template
    
    @Option(
        names = ['--list-templates'],
        description = 'List available predefined templates'
    )
    boolean listTemplates
    
    @Option(
        names = ['--template-help'],
        description = 'Show help for template syntax'
    )
    boolean templateHelp
    
    @Option(
        names = ['--page-size'],
        description = 'Number of results per page',
        paramLabel = 'N'
    )
    Integer pageSize
    
    @Option(
        names = ['--page'],
        description = 'Page number to display (1-indexed)',
        paramLabel = 'N'
    )
    Integer page
    
    @Option(
        names = ['--no-pager'],
        description = 'Disable automatic pagination'
    )
    boolean noPager
    
    @Override
    Integer call() throws Exception {
        // Initialize ANSI console for Windows color support
        if (!noColor) {
            AnsiConsole.systemInstall()
        }
        
        try {
            // Set up logging
            setupLogging(verbose)
            
            // Handle special flags first
            if (listTemplates) {
                // TODO: Implement list templates
                println "Available templates: compact, detailed, summary, verbose"
                return 0
            }
            
            if (templateHelp) {
                // TODO: Implement template help
                println "Template syntax help..."
                return 0
            }
            
            // Validate that we have a query
            if (!query) {
                System.err.println "Error: No query provided"
                return 1
            }
            
            // TODO: Implement main query logic
            println "Processing query: ${query}"
            println "Format: ${format}"
            println "Source: ${source}"
            if (file) {
                println "File: ${file}"
            }
            
            return 0
            
        } finally {
            if (!noColor) {
                AnsiConsole.systemUninstall()
            }
        }
    }
    
    private void setupLogging(boolean verbose) {
        // Configure logging based on verbose flag
        def level = verbose ? 'DEBUG' : 'WARNING'
        System.setProperty('org.slf4j.simpleLogger.defaultLogLevel', level.toLowerCase())
    }
    
    static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args)
        System.exit(exitCode)
    }
}
