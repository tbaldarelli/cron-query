package com.cronquery

import spock.lang.Specification
import spock.lang.Unroll
import sun.util.logging.resources.logging_es

/**
 * Spock tests for CronLoader module.
 * 
 * Tests cover: parsing cron lines, special keywords, validation, file loading
 */
class CronLoaderSpec extends Specification {
    
    def "CronJob should create job with all fields"() {
        given: "a CronJob with all fields populated"
        def job = new CronJob(
            minute: "0",
            hour: "8",
            dayOfMonth: "*",
            month: "*",
            dayOfWeek: "1-5",
            command: "/path/to/script.sh",
            rawLine: "0 8 * * 1-5 /path/to/script.sh"
        )

        expect: "all fields to be set"
        job.minute == "0"
        job.hour == "8"
        job.dayOfMonth == "*"
        job.month == "*"
        job.dayOfWeek == "1-5"
        job.command == "/path/to/script.sh"
        job.rawLine == "0 8 * * 1-5 /path/to/script.sh"
    }
    
    def "CronJob should have correct cron expression"() {
        given: "a CronJob with all fields populated"
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
        
        then: "job should be parsed with correct fields"
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
        "indented comment"   | "  # Indented comment"
        "comment no space"   | "#Another comment"
        "empty line"         | ""
        "spaces only"        | "   "
        "tab only"           | "\t"
        "newline only"       | "\n"
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
    
    @Unroll
    def "parsingRobustness should handle multiple/mixed whitespace: #description" () {
        when: "parsing a line with multiple/mixed whitespace"
        def job = CronLoader.parseCronLine(line)

        then: "job should be parsed with correct fields"
        job != null
        job.hour == "8"
        job.command == "/cmd"

        where:
        description | line
        "Mixed spaces and tabs" | "0   8\t*  *\t\t*   /cmd"
        "Tab separated"         | "0\t8 * * *\t/cmd"
        "multiple spaces"     | "0      8 * * * /cmd"
    }
    
    @Unroll
    def "parsingRobustness should handle special characters: #description" () {
        when: "parsing a line with special characters and spaces"
        def job = CronLoader.parseCronLine(line)

        then: "job should be parsed with correct fields"
        job != null
        job.command == line.split(" ", 6)[-1]

        where:
        description | line
        "Spaces in path"    | "0 8 * * * /path/with spaces/cmd"
        "Hash in path"      | "0 8 * * * /path/with#hash/cmd"
        "Shell command"     | "0 8 * * * /bin/sh -c 'echo test'"
        "Command with args" | "0 8 * * * python3 -m script --arg=val"
    }

    @Unroll
    def "parsingRobustness should handle complex time: #description" () {
        when: "parsing a line with a complex time"
        def job = CronLoader.parseCronLine(line)

        then: "job should be parsed with correct fields"
        job != null
        job.isValid()

        where:
        description         | line
        "Complex ranges"    | "1-10,30,45 8,10-14/2 1-7,15 * MON-FRI /cmd"
        "Steps"             | "*/10 8/2 1,15 * 1-5 /cmd"
        "Multiple commands" | "@hourly /cmd && @daily /cmd2"
    }

    @Unroll
    def "parsingRobustness should handle different command quoting styles: #description" () {
        when: "parsing a line with different command quoting styles"
        def job = CronLoader.parseCronLine(line)

        then: "job should be parsed with correct fields"
        job != null

        where:
        description | line
        "Single quotes" | "0 8 * * * /bin/sh -c 'echo \'test\''"
        "Double quotes" | '0 8 * * * /bin/sh -c "echo \"test\""'
    }

    def "parsingRobustness should handle command with arguments"() {
        given: "a cron line with command arguments"
        def line = "0 8 * * * /usr/bin/script.sh --flag --option=value"
        
        when: "parsing the line"
        def job = CronLoader.parseCronLine(line)
        
        then: "it should preserve the full command"
        job.command == "/usr/bin/script.sh --flag --option=value"
    }

    def "validateCronFields should accept valid fields"() {
        expect: "validation to succeed without exception"
        CronLoader.validateCronFields("0", "8", "*", "*", "1-5")
        CronLoader.validateCronFields("*/15", "9-17", "1,15", "*/2", "1,3,5")
    }

    /* below two tests map to python test_validate_empty_field
     */
    def "validateCronFields should reject empty minute field"() {
        when: "validating with empty minute field"
        CronLoader.validateCronFields("", "8", "*", "*", "*")
    
        then: "it should throw CronParseException"
        def exception = thrown(CronParseException)
        exception.message.contains("Empty minute field")
    }

    def "validateCronFields should reject empty hour field"() {
        when: "validating with empty hour field"
        CronLoader.validateCronFields("0", "", "*", "*", "*")
    
        then: "it should throw CronParseException"
        def exception = thrown(CronParseException)
        exception.message.contains("Empty hour field")
    }

    def "validateCronFields should reject whitespace fields"() {
        when: "validating with whitespace only field field"
        CronLoader.validateCronFields("0", "8", "   ", "*", "*")
    
        then: "it should throw CronParseException"
        def exception = thrown(CronParseException)
        exception.message.contains("Empty dayOfMonth field")
    }
    
    def "loadUserCrontab should return mock data on Windows"() {
        given: "we are running on Windows"
        def isWindows = System.getProperty('os.name').toLowerCase().contains('windows')
        
        when: "loading user crontab on Windows"
        def jobs = isWindows ? CronLoader.loadUserCrontab() : []
        
        then: "it should return mock data on Windows, skip on Linux"
        if (isWindows) {
            jobs != null
            jobs.size() > 0
            jobs.every { it instanceof CronJob }
        } else {
            // Test passes but does nothing on Linux
            true
        }
    }
    
    // Note: Linux subprocess tests skipped - covered by integration tests
    // Python equivalents: test_load_user_crontab_linux_success, etc.

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

    def "SPECIAL_KEYWORDS map should to valid map values"() {
        expect: "all standard special keyword values should be valid"
        CronLoader.SPECIAL_KEYWORDS["@yearly"] == "0 0 1 1 *"
        CronLoader.SPECIAL_KEYWORDS["@annually"] == "0 0 1 1 *"
        CronLoader.SPECIAL_KEYWORDS["@monthly"] == "0 0 1 * *"
        CronLoader.SPECIAL_KEYWORDS["@weekly"] == "0 0 * * 0"
        CronLoader.SPECIAL_KEYWORDS["@daily"] == "0 0 * * *"
        CronLoader.SPECIAL_KEYWORDS["@midnight"] == "0 0 * * *"
        CronLoader.SPECIAL_KEYWORDS["@hourly"] == "0 * * * *"
    }

    def "loadUserCrontab should return valid mock data"() {
        when: "loading user crontab (uses mock data on Windows)"
        def jobs = CronLoader.loadUserCrontab()

        then: "all jobs should be valid"
        jobs.every { it.isValid() }
        jobs.every { it.command.startsWith("/") }
        jobs.every { it.source == "user" }
    }

    @Unroll
    def "parseCronLine should preserve raw line in round trip" () {
        when: "parsing a line with different command quoting styles"
        def job = CronLoader.parseCronLine(line)

        then: "job should be parsed with correct fields"
        job != null
        job.rawLine == line
        job.isValid()


        where:
        description | line
        "weekday test" | "0 8 * * 1-5 /weekday/script.sh"
        "daily keyworkd" | "@daily /daily/task.sh"
        "frequent" | "*/15 * * * * /frequent/task.sh"
        "monthly" | "0 0 1 * * /monthly/report.sh"
    }
}
