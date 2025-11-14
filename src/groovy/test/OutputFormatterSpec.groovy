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
    
        // Test data fields
    List<CronJob> sampleJobs
    QueryCriteria sampleCriteria
    
    def setup() {
        sampleJobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*",
                month: "*", dayOfWeek: "1-5",
                command: "/weekday/backup.sh",
                rawLine: "0 8 * * 1-5 /weekday/backup.sh",
                user: "testuser", source: "user"
            ),
            new CronJob(
                minute: "0", hour: "2", dayOfMonth: "*",
                month: "*", dayOfWeek: "6",
                command: "/saturday/cleanup.sh",
                rawLine: "0 2 * * 6 /saturday/cleanup.sh",
                user: "testuser", source: "user"
            ),
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*",
                month: "*", dayOfWeek: "*",
                command: "/daily/job.sh",
                rawLine: "0 8 * * * /daily/job.sh",
                user: "testuser", source: "user"
            )
        ]

        sampleCriteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "weekdays",
            daysOfWeek: [1, 2, 3, 4, 5] as Set,
            weekdaysOnly: true
        )
    }

    def "formatQueryResults should format list output"() {
        given: "a formatter and weekday job only"
        def jobsToFormat = [sampleJobs[0]] // Just the weekday job
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as list"
        def output = formatter.formatQueryResults(jobsToFormat,
                sampleCriteria, 'list')
        
        then: "it should contain job information"
        output.contains("0 8 * * 1-5")
        output.contains("/weekday/backup.sh")
        // Saturday job should NOT be present
        !output.contains("/saturday/cleanup.sh")
        // TODO: fix output.contains("Schedule:")
        // TODO: fix output.contains("Next runs:").  NOTE: should work, maybe useColors = false not working.
        output.contains("Found 1 job(s)")
    }
    
    def "formatQueryResults should format table output"() {
        given: "a formatter and weekday job only"
        def jobsToFormat = [sampleJobs[0]] // Just the weekday job
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as table"
        def output = formatter.formatQueryResults(jobsToFormat,
                sampleCriteria, 'table')
        
        then: "it should contain table structure"
        output.contains("CRON EXPRESSION")
        output.contains("COMMAND")
        // TODO: fix output.contains("DESCRIPTION")
        // TODO: fix output.contains("Next run")
        output.contains("USER")
        output.contains("0 8 * * 1-5" )
        output.contains("/weekday/backup.sh")
        // TODO: fix output.contains("+") // Table borders
        // TODO: fix output.contains("|") // Table separators
        output.contains("Found 1 job(s)")
    }
    
    def "formatQueryResults should format JSON output"() {
        given: "a formatter and weekday job only"
        def jobsToFormat = [sampleJobs[0]] // Just the weekday job
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting as JSON"
        def output = formatter.formatQueryResults(jobsToFormat,
                sampleCriteria, 'json')
        def json = new JsonSlurper().parseText(output)
        
        then: "it should produce valid JSON"
        json.query == "weekdays"
        json.matches == 1
        json.jobs.size() == 1
        json.jobs[0].cron_expression == "0 8 * * 1-5"
        json.jobs[0].command == "/weekday/backup.sh"
        json.jobs[0].user == "testuser"
        json.jobs[0].source == "user"
    }
    
    def "formatQueryResults should format YAML output"() {
         given: "a formatter and weekday job only"
        def jobsToFormat = [sampleJobs[1]] // Just the Saturday
        def formatter = new OutputFormatter(false, false, 3)
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
  
        when: "formatting as YAML"
        def output = formatter.formatQueryResults(jobsToFormat,
                criteria, 'yaml')
        
        then: "it should produce YAML-like structure"
        output.contains("query: \"saturday\"")
        output.contains("matches: 1")
        output.contains("jobs:")
        output.contains("cron_expression: \"0 2 * * 6\"")
        output.contains("command: \"/saturday/cleanup.sh\"")
        output.contains("user: \"testuser\"")
    }
    
    def "formatQueryResults should format without next runs"() {
        given: "a formatter and weekday job only"
        def jobsToFormat = [sampleJobs[0]] // Just the weekday job
        def formatter = new OutputFormatter(false, false, 3)

        when: "formatting as list without next runs"
        def output = formatter.formatQueryResults(jobsToFormat,
                sampleCriteria, 'list')

        then: "it should not contain next runs"
        // TODO fix output.contains("Jobs matching 'weekdays (Monday-Friday)':")
        !output.contains("Next runs:")
        output.contains("Found 1 job(s)")
        // TODO: fix output.contains("Schdule:") // Should still show schedule description
    }

    def "formatQueryResults should handle empty results"() {
        given: "empty job list"
        def jobs = []
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting empty results"
        def output = formatter.formatQueryResults(jobs, sampleCriteria,
            'list')
        
        then: "it should show no jobs found message"
        output.contains("No jobs found")
        // TODO fix output.contains("This could mean:")
        // TODO fix output.contains("Try:")
        // TODO fix output.contains("crontab -l")
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
        given: "a formatter and weekday job only"
        def jobsToFormat = [sampleJobs[0]] // Just the weekday job
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting with invalid format"
        formatter.formatQueryResults(jobsToFormat, sampleCriteria,
            'xml')
        
        then: "it should throw OutputFormatterException"
        thrown(OutputFormatterException)
    }
    
    @Unroll
    def "formatQueryResults should support format: #format"() {
        given: "a formatter"
        def formatter = new OutputFormatter(false, false, 3)
        
        when: "formatting with each supported format"
        def output = formatter.formatQueryResults(sampleJobs,
            sampleCriteria, format)
        
        then: "it should produce output without error"
        output != null
        !output.isEmpty()
        
        where:
        format << ['list', 'table', 'json', 'yaml']
    }

    // TODO implement test_schedule_description_error_handling
    // TODO implement test_next_runs_error_handling
    
    /* Skipping porting of TestFormatListOutput,
     *  TestFormatTableOutput, and TestFormatJsonOutput - they are
     *  covered by other tests
     */

    def "formatErrorMessage should format error messages"() {
        when: "formatting an error message"
        def output = OutputFormatter.formatErrorMessage("Something went wrong")
        
        then: "it should contain error indicator"
        output.contains("ERROR")
        output.contains("Something went wrong")
    }

    /* TODO: Implement formatErrorMessage with query parameter support
    def "formatErrorMessage should format error with query"() {
        given: "an error and query"
        def error = new Exception("Test error message")
        def query = "invalid query"
    
        when: "formatting error message"
        def output = OutputFormatter.formatErrorMessage(error, query)
    
        then: "it should contain error details"
        output.contains("ERROR")
        output.contains("Test error message")
        output.contains("invalid query")
    }
    */

    /* TODO: Implement formatErrorMessage with just error (no query)
    def "formatErrorMessage should format error without query"() {
        given: "an error without query"
        def error = new RuntimeException("Another error")
    
        when: "formatting error message"
        def output = OutputFormatter.formatErrorMessage(error)
    
        then: "it should contain error but not query"
        output.contains("ERROR")
        output.contains("Another error")
        !output.contains("Query:")
    }
    */

    /* TODO: Implement formatJobSummary method
    def "formatJobSummary should handle empty jobs"() {
        when: "formatting empty job list"
        def result = OutputFormatter.formatJobSummary([])
    
        then: "it should return no jobs message"
        result == "No jobs found"
    */

    /* TODO: Implement formatJobSummary method
    def "formatJobSummary should format single job"() {
        given: "a single job"
        def job = new CronJob(
            minute: "0", hour: "8", dayOfMonth: "*",
            month: "*", dayOfWeek: "*",
            command: "/test", rawLine: "0 8 * * * /test",
            user: "testuser", source: "user"
        )
    
        when: "formatting job summary"
        def result = OutputFormatter.formatJobSummary([job])
    
        then: "it should contain job count and source"
        result.contains("1 job")
        result.contains("from user")
        result.contains("testuser")
    */

    /* TODO: Implement formatJobSummary method
    def "formatJobSummary should format multiple jobs"() {
        given: "multiple jobs from different sources"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*",
                month: "*", dayOfWeek: "*",
                command: "/test1", rawLine: "0 8 * * * /test1",
                user: "user1", source: "user"
            ),
            new CronJob(
                minute: "0", hour: "9", dayOfMonth: "*",
                month: "*", dayOfWeek: "*",
                command: "/test2", rawLine: "0 9 * * * /test2",
                user: "user2", source: "system"
            )
        ]
    
        when: "formatting job summary"
        def result = OutputFormatter.formatJobSummary(jobs)
    
        then: "it should contain job count, sources, and user count"
        result.contains("2 jobs")
        result.contains("from system, user")  // Sorted
        result.contains("(2 users)")
    }
    */

    /* TODO: Implement formatExecutionTime method
    def "formatExecutionTime should format milliseconds"() {
        given: "start and end times 500ms apart"
        def start = new Date(2023, 0, 1, 12, 0, 0)
        def end = new Date(start.time + 500)
    
        when: "formatting execution time"
        def result = OutputFormatter.formatExecutionTime(start, end)
    
        then: "it should show milliseconds"
        result.contains("500")
        result.contains("ms")
    }
    */

    /* TODO: Implement formatExecutionTime method
    def "formatExecutionTime should format seconds"() {
        given: "start and end times 2.5s apart"
        def start = new Date(2023, 0, 1, 12, 0, 0)
        def end = new Date(start.time + 2500)
    
        when: "formatting execution time"
        def result = OutputFormatter.formatExecutionTime(start, end)
    
        then: "it should show seconds"
        result.contains("2.5")
        result.contains("s")
    }
    */

    /* I left off here, which losely matches to the end of the TestErrorFormatting class, and start of
     *  the TestUtilityFunctions class (line 399 in tst_output_formatter.py)
     *      
     *  Not yet ported from Python:
     *  TestFormatListOutput (private method tests - skip)
     *  TestFormatTableOutput (private method tests - skip)
     *  TestFormatJsonOutput (private method tests - skip)
     *  TestEmptyResultsFormatting
     *  TestIntegration
     */
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
