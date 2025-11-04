package com.cronquery

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Spock tests for CronLoader module.
 * 
 * Tests cover: parsing cron lines, special keywords, validation, file loading
 */
class CronLoaderSpec extends Specification {
    
    def "CronJob should have correct cron expression"() {
        given: "a CronJob instance"
        def job = new CronJob(
            minute: "30",
            hour: "14",
            dayOfMonth: "15",
            month: "*",
            dayOfWeek: "*",
            command: "/test/cmd",
            rawLine: "test"
        )
        
        when: "getting the cron expression"
        def expression = job.cronExpression
        
        then: "it should concatenate all fields"
        expression == "30 14 15 * *"
    }
    
    def "CronJob should validate valid cron expressions"() {
        given: "a valid CronJob"
        def job = new CronJob(
            minute: "0",
            hour: "8",
            dayOfMonth: "*",
            month: "*",
            dayOfWeek: "*",
            command: "/test/cmd",
            rawLine: "test"
        )
        
        when: "checking validity"
        def valid = job.isValid()
        
        then: "it should be valid"
        valid == true
    }
    
    def "CronJob should reject invalid cron expressions"() {
        given: "an invalid CronJob with out-of-range minute"
        def job = new CronJob(
            minute: "99",  // Invalid
            hour: "8",
            dayOfMonth: "*",
            month: "*",
            dayOfWeek: "*",
            command: "/test/cmd",
            rawLine: "test"
        )
        
        when: "checking validity"
        def valid = job.isValid()
        
        then: "it should be invalid"
        valid == false
    }
    
    def "parseCronLine should parse standard cron format"() {
        given: "a standard cron line"
        def line = "0 8 * * 1-5 /home/user/weekday-script.sh"
        
        when: "parsing the line"
        def job = CronLoader.parseCronLine(line)
        
        then: "it should parse correctly"
        job != null
        job.minute == "0"
        job.hour == "8"
        job.dayOfMonth == "*"
        job.month == "*"
        job.dayOfWeek == "1-5"
        job.command == "/home/user/weekday-script.sh"
        job.rawLine == line
        job.source == "user"
    }
    
    def "parseCronLine should parse complex cron expressions"() {
        given: "a complex cron line with multiple operators"
        def line = "*/15 9-17 1,15 */2 1,3,5 /complex/script.sh --arg value"
        
        when: "parsing the line"
        def job = CronLoader.parseCronLine(line)
        
        then: "it should parse all fields correctly"
        job != null
        job.minute == "*/15"
        job.hour == "9-17"
        job.dayOfMonth == "1,15"
        job.month == "*/2"
        job.dayOfWeek == "1,3,5"
        job.command == "/complex/script.sh --arg value"
    }
    
    @Unroll
    def "parseCronLine should parse special keyword: #keyword"() {
        given: "a cron line with special keyword"
        def line = "${keyword} /task/script.sh"
        
        when: "parsing the line"
        def job = CronLoader.parseCronLine(line)
        
        then: "it should convert to standard format"
        job != null
        job.cronExpression == expectedExpression
        job.command == "/task/script.sh"
        job.rawLine == line
        
        where:
        keyword      | expectedExpression
        "@daily"     | "0 0 * * *"
        "@hourly"    | "0 * * * *"
        "@weekly"    | "0 0 * * 0"
        "@monthly"   | "0 0 1 * *"
        "@yearly"    | "0 0 1 1 *"
        "@annually"  | "0 0 1 1 *"
        "@midnight"  | "0 0 * * *"
    }
    
    @Unroll
    def "parseCronLine should skip: #description"() {
        when: "parsing a line that should be skipped"
        def result = CronLoader.parseCronLine(line)
        
        then: "it should return null"
        result == null
        
        where:
        description           | line
        "comment"            | "# This is a comment"
        "empty line"         | ""
        "whitespace only"    | "   "
        "environment var"    | "MAILTO=user@example.com"
        "PATH variable"      | "PATH=/usr/bin:/bin"
        "SHELL variable"     | "SHELL=/bin/bash"
    }
    
    def "parseCronLine should throw error for too few fields"() {
        given: "a line with insufficient fields"
        def line = "0 8 *"
        
        when: "parsing the line"
        CronLoader.parseCronLine(line)
        
        then: "it should throw CronParseException"
        def exception = thrown(CronParseException)
        exception.message.contains("expected 6 or 7 fields, got 3")
    }
    
    def "parseCronLine should throw error for invalid special keyword"() {
        given: "a line with unknown special keyword"
        def line = "@invalid /some/command"
        
        when: "parsing the line"
        CronLoader.parseCronLine(line)
        
        then: "it should throw CronParseException"
        def exception = thrown(CronParseException)
        exception.message.contains("Unknown special keyword: @invalid")
    }
    
    def "parseCronLine should throw error for malformed special keyword"() {
        given: "a special keyword without command"
        def line = "@daily"
        
        when: "parsing the line"
        CronLoader.parseCronLine(line)
        
        then: "it should throw CronParseException"
        def exception = thrown(CronParseException)
        exception.message.contains("Invalid special keyword format")
    }
    
    def "parseCronLine should accept custom source and user"() {
        given: "a cron line and custom metadata"
        def line = "0 8 * * * /test/cmd"
        
        when: "parsing with custom source and user"
        def job = CronLoader.parseCronLine(line, "system", "testuser")
        
        then: "it should set the metadata correctly"
        job.source == "system"
        job.user == "testuser"
    }
    
    def "parseCronLine should reject invalid cron expressions"() {
        given: "a line with invalid cron values"
        def line = "99 25 32 13 8 /invalid/cmd"
        
        when: "parsing the line"
        CronLoader.parseCronLine(line)
        
        then: "it should throw CronParseException"
        thrown(CronParseException)
    }
    
    def "parseCronLine should handle command with arguments"() {
        given: "a cron line with command arguments"
        def line = "0 8 * * * /usr/bin/script.sh --flag --option=value"
        
        when: "parsing the line"
        def job = CronLoader.parseCronLine(line)
        
        then: "it should preserve the full command"
        job.command == "/usr/bin/script.sh --flag --option=value"
    }
    
    def "loadUserCrontab should return mock data on Windows"() {
        when: "loading user crontab on Windows"
        def jobs = CronLoader.loadUserCrontab()
        
        then: "it should return mock data"
        jobs != null
        jobs.size() > 0
        jobs.every { it instanceof CronJob }
    }
    
    def "loadCrontabFromFile should throw FileNotFoundException for missing file"() {
        given: "a non-existent file path"
        def filePath = "/nonexistent/path/to/crontab.txt"
        
        when: "loading from the file"
        CronLoader.loadCrontabFromFile(filePath)
        
        then: "it should throw FileNotFoundException"
        thrown(FileNotFoundException)
    }
    
    def "loadCrontabFromFile should load valid cron jobs from file"() {
        given: "a temporary crontab file"
        def tempFile = File.createTempFile("test_crontab", ".txt")
        tempFile.deleteOnExit()
        tempFile.text = """
# Comment line
0 8 * * * /morning/job.sh
30 12 * * 1-5 /lunch/reminder.sh
@hourly /hourly/task.sh

MAILTO=test@example.com
0 0 * * 0 /weekly/backup.sh
"""
        
        when: "loading from the file"
        def jobs = CronLoader.loadCrontabFromFile(tempFile.absolutePath)
        
        then: "it should parse valid jobs and skip comments/env vars"
        jobs.size() == 4
        jobs[0].command == "/morning/job.sh"
        jobs[1].command == "/lunch/reminder.sh"
        jobs[2].command == "/hourly/task.sh"
        jobs[3].command == "/weekly/backup.sh"
        
        cleanup:
        tempFile.delete()
    }
    
    def "loadCrontabFromFile should handle parse errors gracefully"() {
        given: "a file with some invalid lines"
        def tempFile = File.createTempFile("test_crontab", ".txt")
        tempFile.deleteOnExit()
        tempFile.text = """
0 8 * * * /valid/job.sh
invalid line here
30 12 * * 1-5 /another/valid.sh
99 99 99 99 99 /invalid/job.sh
"""
        
        when: "loading from the file"
        def jobs = CronLoader.loadCrontabFromFile(tempFile.absolutePath)
        
        then: "it should return only valid jobs"
        jobs.size() == 2
        jobs[0].command == "/valid/job.sh"
        jobs[1].command == "/another/valid.sh"
        
        cleanup:
        tempFile.delete()
    }
    
    def "SPECIAL_KEYWORDS map should contain all standard keywords"() {
        expect: "all standard special keywords to be defined"
        CronLoader.SPECIAL_KEYWORDS.containsKey("@yearly")
        CronLoader.SPECIAL_KEYWORDS.containsKey("@annually")
        CronLoader.SPECIAL_KEYWORDS.containsKey("@monthly")
        CronLoader.SPECIAL_KEYWORDS.containsKey("@weekly")
        CronLoader.SPECIAL_KEYWORDS.containsKey("@daily")
        CronLoader.SPECIAL_KEYWORDS.containsKey("@midnight")
        CronLoader.SPECIAL_KEYWORDS.containsKey("@hourly")
    }
}
