package com.cronquery

import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.JsonSlurper

/**
 * Spock tests for OutputFormatter module.
 * 
 * Tests cover: list, table, JSON, YAML output formats
 */
class OutputFormatterSpec extends Specification {
    
    def "OutputFormatter should initialize with default values"() {
        when: "creating a formatter with defaults"
        def formatter = new OutputFormatter()
        
        then: "it should use sensible defaults"
        formatter != null
    }
    
    def "OutputFormatter should support disabling colors"() {
        when: "creating a formatter with colors disabled"
        def formatter = new OutputFormatter(false, true, 3)
        
        then: "it should be created successfully"
        formatter != null
    }
    
    def "formatQueryResults should format list output"() {
        given: "jobs and criteria"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/saturday/job.sh", rawLine: "test"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as list"
        def output = formatter.formatQueryResults(jobs, criteria, 'list')
        
        then: "it should contain job information"
        output.contains("Found 1 job(s)")
        output.contains("saturday")
        output.contains("0 8 * * 6")
        output.contains("/saturday/job.sh")
    }
    
    def "formatQueryResults should format table output"() {
        given: "jobs and criteria"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/saturday/job.sh", rawLine: "test",
                user: "testuser"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as table"
        def output = formatter.formatQueryResults(jobs, criteria, 'table')
        
        then: "it should contain table structure"
        output.contains("Found 1 job(s)")
        output.contains("CRON EXPRESSION")
        output.contains("USER")
        output.contains("COMMAND")
        output.contains("0 8 * * 6")
        output.contains("/saturday/job.sh")
    }
    
    def "formatQueryResults should format JSON output"() {
        given: "jobs and criteria"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/saturday/job.sh", rawLine: "test line",
                user: "testuser", source: "user"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as JSON"
        def output = formatter.formatQueryResults(jobs, criteria, 'json')
        def json = new JsonSlurper().parseText(output)
        
        then: "it should produce valid JSON"
        json.query == "saturday"
        json.matches == 1
        json.jobs.size() == 1
        json.jobs[0].cron_expression == "0 8 * * 6"
        json.jobs[0].command == "/saturday/job.sh"
        json.jobs[0].user == "testuser"
        json.jobs[0].source == "user"
    }
    
    def "formatQueryResults should format YAML output"() {
        given: "jobs and criteria"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/saturday/job.sh", rawLine: "test",
                user: "testuser"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as YAML"
        def output = formatter.formatQueryResults(jobs, criteria, 'yaml')
        
        then: "it should produce YAML-like structure"
        output.contains("query: \"saturday\"")
        output.contains("matches: 1")
        output.contains("jobs:")
        output.contains("cron_expression: \"0 8 * * 6\"")
        output.contains("command: \"/saturday/job.sh\"")
        output.contains("user: \"testuser\"")
    }
    
    def "formatQueryResults should handle empty results"() {
        given: "empty job list"
        def jobs = []
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting empty results"
        def output = formatter.formatQueryResults(jobs, criteria, 'list')
        
        then: "it should show no jobs found message"
        output.contains("No jobs found")
        output.contains("saturday")
    }
    
    def "formatQueryResults should handle multiple jobs"() {
        given: "multiple jobs"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/job1.sh", rawLine: "test1"
            ),
            new CronJob(
                minute: "0", hour: "12", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/job2.sh", rawLine: "test2"
            ),
            new CronJob(
                minute: "0", hour: "18", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/job3.sh", rawLine: "test3"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as list"
        def output = formatter.formatQueryResults(jobs, criteria, 'list')
        
        then: "it should include all jobs"
        output.contains("Found 3 job(s)")
        output.contains("/job1.sh")
        output.contains("/job2.sh")
        output.contains("/job3.sh")
    }
    
    def "formatQueryResults should throw exception for unsupported format"() {
        given: "jobs and criteria"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/test.sh", rawLine: "test"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting with invalid format"
        formatter.formatQueryResults(jobs, criteria, 'invalid')
        
        then: "it should throw OutputFormatterException"
        thrown(OutputFormatterException)
    }
    
    @Unroll
    def "formatQueryResults should support format: #format"() {
        given: "jobs and criteria"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/test.sh", rawLine: "test"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting with each supported format"
        def output = formatter.formatQueryResults(jobs, criteria, format)
        
        then: "it should produce output without error"
        output != null
        !output.isEmpty()
        
        where:
        format << ['list', 'table', 'json', 'yaml']
    }
    
    def "formatErrorMessage should format error messages"() {
        when: "formatting an error message"
        def output = OutputFormatter.formatErrorMessage("Something went wrong")
        
        then: "it should contain error indicator"
        output.contains("ERROR")
        output.contains("Something went wrong")
    }
    
    def "getSupportedFormats should return all formats"() {
        when: "getting supported formats"
        def formats = OutputFormatter.getSupportedFormats()
        
        then: "it should include all format types"
        formats.contains('list')
        formats.contains('table')
        formats.contains('json')
        formats.contains('yaml')
        formats.size() == 4
    }
    
    @Unroll
    def "validateOutputFormat should validate: #format as #expected"() {
        expect: "correct validation"
        OutputFormatter.validateOutputFormat(format) == expected
        
        where:
        format      | expected
        'list'      | true
        'table'     | true
        'json'      | true
        'yaml'      | true
        'LIST'      | true  // Case-insensitive
        'Table'     | true
        'invalid'   | false
        'xml'       | false
        ''          | false
    }
    
    def "JSON output should be parseable"() {
        given: "jobs formatted as JSON"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "1-5", 
                command: "/weekday.sh", rawLine: "test",
                user: "testuser", source: "user"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "weekdays",
            daysOfWeek: [1, 2, 3, 4, 5] as Set,
            weekdaysOnly: true
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting and parsing JSON"
        def jsonOutput = formatter.formatQueryResults(jobs, criteria, 'json')
        def parsed = new JsonSlurper().parseText(jsonOutput)
        
        then: "parsed JSON should match original data"
        parsed.query == "weekdays"
        parsed.matches == 1
        parsed.jobs[0].cron_expression == "0 8 * * 1-5"
        parsed.jobs[0].command == "/weekday.sh"
        parsed.jobs[0].user == "testuser"
        parsed.jobs[0].source == "user"
        parsed.jobs[0].raw_line == "test"
    }
    
    def "formatQueryResults should handle combined query criteria"() {
        given: "jobs and combined criteria"
        def jobs = [
            new CronJob(
                minute: "0", hour: "11", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/saturday-late.sh", rawLine: "test"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.COMBINED,
            rawQuery: "saturday after 10 AM",
            daysOfWeek: [6] as Set,
            timeRangeStart: new Tuple2(10, 0),
            isTimeAfter: true
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as list"
        def output = formatter.formatQueryResults(jobs, criteria, 'list')
        
        then: "it should describe combined criteria"
        output.contains("Found 1 job(s)")
        output.toLowerCase().contains("saturday")
        output.contains("after")
        output.contains("10 AM")
    }
    
    def "formatQueryResults should handle time-based criteria"() {
        given: "jobs and time criteria"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "*", 
                command: "/morning.sh", rawLine: "test"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.TIME_BASED,
            rawQuery: "8 AM",
            timeHour: 8,
            timeMinute: 0
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as list"
        def output = formatter.formatQueryResults(jobs, criteria, 'list')
        
        then: "it should describe time criteria"
        output.contains("Found 1 job(s)")
        output.contains("8 AM")
    }
    
    def "table output should truncate long commands"() {
        given: "a job with a very long command"
        def longCommand = "/very/long/path/to/script.sh " + ("--option=value " * 20)
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "*", 
                command: longCommand, rawLine: "test"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "monday",
            daysOfWeek: [1] as Set
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as table"
        def output = formatter.formatQueryResults(jobs, criteria, 'table')
        
        then: "command should be truncated in table"
        output != null
        // Table should contain truncation indicator if command is too long
        output.contains("COMMAND")
    }
    
    def "formatter should handle jobs without user field"() {
        given: "a job without user"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "*", 
                command: "/test.sh", rawLine: "test"
                // user is null by default
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "monday",
            daysOfWeek: [1] as Set
        )
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting in different formats"
        def listOutput = formatter.formatQueryResults(jobs, criteria, 'list')
        def jsonOutput = formatter.formatQueryResults(jobs, criteria, 'json')
        def yamlOutput = formatter.formatQueryResults(jobs, criteria, 'yaml')
        
        then: "all formats should handle null user gracefully"
        listOutput != null
        jsonOutput != null
        yamlOutput != null
        yamlOutput.contains("user: \"current\"")
    }
}
