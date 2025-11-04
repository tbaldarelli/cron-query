package com.cronquery

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Spock tests for ScheduleAnalyzer module.
 * 
 * Tests cover: job matching, day-of-week logic, time ranges, field parsing
 */
class ScheduleAnalyzerSpec extends Specification {
    
    def "findMatchingJobs should find jobs matching day-based criteria"() {
        given: "a list of jobs and Saturday criteria"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "6", 
                command: "/saturday/job.sh", rawLine: "test1"
            ),
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "1-5", 
                command: "/weekday/job.sh", rawLine: "test2"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
        
        when: "finding matching jobs"
        def matches = ScheduleAnalyzer.findMatchingJobs(jobs, criteria)
        
        then: "only Saturday job should match"
        matches.size() == 1
        matches[0].command == "/saturday/job.sh"
    }
    
    def "findMatchingJobs should return empty list for UNKNOWN query type"() {
        given: "jobs and unknown criteria"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "*", 
                command: "/test/job.sh", rawLine: "test"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.UNKNOWN,
            rawQuery: "unknown"
        )
        
        when: "finding matching jobs"
        def matches = ScheduleAnalyzer.findMatchingJobs(jobs, criteria)
        
        then: "no matches should be found"
        matches.isEmpty()
    }
    
    def "runsOnDayOfWeek should match wildcard day-of-week"() {
        given: "a job that runs every day"
        def job = new CronJob(
            minute: "0", hour: "8", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        
        when: "checking against any day"
        def result = ScheduleAnalyzer.runsOnDayOfWeek(job, [3] as Set)
        
        then: "it should match"
        result == true
    }
    
    @Unroll
    def "runsOnDayOfWeek should match specific day: #dayOfWeek with target #targetDay"() {
        given: "a job with specific day-of-week"
        def job = new CronJob(
            minute: "0", hour: "8", dayOfMonth: "*", 
            month: "*", dayOfWeek: dayOfWeek, 
            command: "/test/job.sh", rawLine: "test"
        )
        
        when: "checking against target day"
        def result = ScheduleAnalyzer.runsOnDayOfWeek(job, [targetDay] as Set)
        
        then: "it should match as expected"
        result == expectedMatch
        
        where:
        dayOfWeek | targetDay | expectedMatch
        "1"       | 1         | true
        "1"       | 2         | false
        "1-5"     | 3         | true
        "1-5"     | 6         | false
        "0,6"     | 0         | true
        "0,6"     | 6         | true
        "0,6"     | 3         | false
    }
    
    def "runsOnDayOfWeek should handle Sunday as 7 or 0"() {
        given: "a job that runs on Sunday (using 7)"
        def job = new CronJob(
            minute: "0", hour: "8", dayOfMonth: "*", 
            month: "*", dayOfWeek: "7", 
            command: "/test/job.sh", rawLine: "test"
        )
        
        when: "checking against Sunday as 0"
        def result = ScheduleAnalyzer.runsOnDayOfWeek(job, [0] as Set)
        
        then: "it should match"
        result == true
    }
    
    def "runsOnDayOfWeek should return false when only day-of-month is specified"() {
        given: "a job with only day-of-month"
        def job = new CronJob(
            minute: "0", hour: "8", dayOfMonth: "15", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        
        when: "checking against a day-of-week"
        def result = ScheduleAnalyzer.runsOnDayOfWeek(job, [1] as Set)
        
        then: "it should not match (DOM doesn't imply DOW)"
        result == false
    }
    
    def "runsAtTime should match specific time"() {
        given: "a job that runs at 8:30"
        def job = new CronJob(
            minute: "30", hour: "8", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        
        when: "checking against 8:30"
        def result = ScheduleAnalyzer.runsAtTime(job, 8, 30)
        
        then: "it should match"
        result == true
    }
    
    def "runsAtTime should not match different time"() {
        given: "a job that runs at 8:30"
        def job = new CronJob(
            minute: "30", hour: "8", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        
        when: "checking against 9:00"
        def result = ScheduleAnalyzer.runsAtTime(job, 9, 0)
        
        then: "it should not match"
        result == false
    }
    
    def "runsAtTime should match wildcard hour"() {
        given: "a job that runs every hour at :30"
        def job = new CronJob(
            minute: "30", hour: "*", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        
        when: "checking any hour at :30"
        def result = ScheduleAnalyzer.runsAtTime(job, 15, 30)
        
        then: "it should match"
        result == true
    }
    
    def "runsInTimeRange should match 'after' time range"() {
        given: "a job that runs at 11:00"
        def job = new CronJob(
            minute: "0", hour: "11", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        def criteria = new QueryCriteria(
            queryType: QueryType.TIME_BASED,
            rawQuery: "after 10 AM",
            timeRangeStart: new Tuple2(10, 0),
            isTimeAfter: true
        )
        
        when: "checking if job runs after 10 AM"
        def result = ScheduleAnalyzer.runsInTimeRange(job, criteria)
        
        then: "it should match"
        result == true
    }
    
    def "runsInTimeRange should not match 'after' when before threshold"() {
        given: "a job that runs at 9:00"
        def job = new CronJob(
            minute: "0", hour: "9", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        def criteria = new QueryCriteria(
            queryType: QueryType.TIME_BASED,
            rawQuery: "after 10 AM",
            timeRangeStart: new Tuple2(10, 0),
            isTimeAfter: true
        )
        
        when: "checking if job runs after 10 AM"
        def result = ScheduleAnalyzer.runsInTimeRange(job, criteria)
        
        then: "it should not match"
        result == false
    }
    
    def "runsInTimeRange should match 'before' time range"() {
        given: "a job that runs at 9:00"
        def job = new CronJob(
            minute: "0", hour: "9", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        def criteria = new QueryCriteria(
            queryType: QueryType.TIME_BASED,
            rawQuery: "before 10 AM",
            timeRangeEnd: new Tuple2(10, 0),
            isTimeBefore: true
        )
        
        when: "checking if job runs before 10 AM"
        def result = ScheduleAnalyzer.runsInTimeRange(job, criteria)
        
        then: "it should match"
        result == true
    }
    
    def "runsInTimeRange should match 'between' time range"() {
        given: "a job that runs at 12:00"
        def job = new CronJob(
            minute: "0", hour: "12", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        def criteria = new QueryCriteria(
            queryType: QueryType.TIME_BASED,
            rawQuery: "between 9 AM and 5 PM",
            timeRangeStart: new Tuple2(9, 0),
            timeRangeEnd: new Tuple2(17, 0),
            isTimeBetween: true
        )
        
        when: "checking if job runs between 9 AM and 5 PM"
        def result = ScheduleAnalyzer.runsInTimeRange(job, criteria)
        
        then: "it should match"
        result == true
    }
    
    def "runsInTimeRange should not match outside 'between' range"() {
        given: "a job that runs at 18:00"
        def job = new CronJob(
            minute: "0", hour: "18", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        def criteria = new QueryCriteria(
            queryType: QueryType.TIME_BASED,
            rawQuery: "between 9 AM and 5 PM",
            timeRangeStart: new Tuple2(9, 0),
            timeRangeEnd: new Tuple2(17, 0),
            isTimeBetween: true
        )
        
        when: "checking if job runs between 9 AM and 5 PM"
        def result = ScheduleAnalyzer.runsInTimeRange(job, criteria)
        
        then: "it should not match"
        result == false
    }
    
    def "matchesCriteria should match combined day and time criteria"() {
        given: "a job that runs on Saturday at 11:00"
        def job = new CronJob(
            minute: "0", hour: "11", dayOfMonth: "*", 
            month: "*", dayOfWeek: "6", 
            command: "/test/job.sh", rawLine: "test"
        )
        def criteria = new QueryCriteria(
            queryType: QueryType.COMBINED,
            rawQuery: "saturday after 10 AM",
            daysOfWeek: [6] as Set,
            timeRangeStart: new Tuple2(10, 0),
            isTimeAfter: true
        )
        
        when: "checking if job matches combined criteria"
        def result = ScheduleAnalyzer.matchesCriteria(job, criteria)
        
        then: "it should match"
        result == true
    }
    
    def "matchesCriteria should not match when day matches but time doesn't"() {
        given: "a job that runs on Saturday at 9:00"
        def job = new CronJob(
            minute: "0", hour: "9", dayOfMonth: "*", 
            month: "*", dayOfWeek: "6", 
            command: "/test/job.sh", rawLine: "test"
        )
        def criteria = new QueryCriteria(
            queryType: QueryType.COMBINED,
            rawQuery: "saturday after 10 AM",
            daysOfWeek: [6] as Set,
            timeRangeStart: new Tuple2(10, 0),
            isTimeAfter: true
        )
        
        when: "checking if job matches combined criteria"
        def result = ScheduleAnalyzer.matchesCriteria(job, criteria)
        
        then: "it should not match"
        result == false
    }
    
    @Unroll
    def "parseCronField should parse: #field as #expected"() {
        when: "parsing a cron field"
        def result = ScheduleAnalyzer.parseCronField(field, minVal, maxVal)
        
        then: "it should parse correctly"
        result == expected as Set
        
        where:
        field     | minVal | maxVal | expected
        "*"       | 0      | 5      | [0, 1, 2, 3, 4, 5]
        "3"       | 0      | 5      | [3]
        "1-4"     | 0      | 5      | [1, 2, 3, 4]
        "1,3,5"   | 0      | 5      | [1, 3, 5]
        "*/2"     | 0      | 5      | [0, 2, 4]
        "1-4/2"   | 0      | 5      | [1, 3]
    }
    
    def "parseCronField should handle complex expressions"() {
        given: "a complex cron field"
        def field = "1-5,10,15-20/2"
        
        when: "parsing the field"
        def result = ScheduleAnalyzer.parseCronField(field, 0, 23)
        
        then: "it should parse all components"
        result.contains(1)
        result.contains(2)
        result.contains(3)
        result.contains(4)
        result.contains(5)
        result.contains(10)
        result.contains(15)
        result.contains(17)
        result.contains(19)
        !result.contains(16)
        !result.contains(18)
    }
    
    def "parseCronField should convert Sunday from 7 to 0"() {
        given: "a day-of-week field with Sunday as 7"
        def field = "7"
        
        when: "parsing as day-of-week (0-6)"
        def result = ScheduleAnalyzer.parseCronField(field, 0, 6)
        
        then: "it should convert to 0"
        result == [0] as Set
    }
    
    def "getNextRuns should return future execution times"() {
        given: "a job that runs every day at 8:00"
        def job = new CronJob(
            minute: "0", hour: "8", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        
        when: "getting next 3 runs"
        def nextRuns = ScheduleAnalyzer.getNextRuns(job, 3)
        
        then: "it should return 3 future times"
        nextRuns.size() == 3
        nextRuns.every { it instanceof ZonedDateTime }
        nextRuns.every { it.isAfter(ZonedDateTime.now()) }
        
        and: "all should be at 8:00"
        nextRuns.every { it.hour == 8 && it.minute == 0 }
    }
    
    def "getNextRuns should throw exception for invalid job"() {
        given: "an invalid job"
        def job = new CronJob(
            minute: "99", hour: "8", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        
        when: "getting next runs"
        ScheduleAnalyzer.getNextRuns(job, 3)
        
        then: "it should throw ScheduleAnalysisException"
        thrown(ScheduleAnalysisException)
    }
    
    def "findMatchingJobs should handle invalid jobs gracefully"() {
        given: "a list with valid and invalid jobs"
        def jobs = [
            new CronJob(
                minute: "0", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "*", 
                command: "/valid/job.sh", rawLine: "test1"
            ),
            new CronJob(
                minute: "99", hour: "8", dayOfMonth: "*", 
                month: "*", dayOfWeek: "*", 
                command: "/invalid/job.sh", rawLine: "test2"
            )
        ]
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "monday",
            daysOfWeek: [1] as Set
        )
        
        when: "finding matching jobs"
        def matches = ScheduleAnalyzer.findMatchingJobs(jobs, criteria)
        
        then: "it should skip invalid jobs without crashing"
        noExceptionThrown()
    }
    
    def "runsOnDayOfWeek should handle weekday ranges"() {
        given: "a job that runs Monday-Friday"
        def job = new CronJob(
            minute: "0", hour: "8", dayOfMonth: "*", 
            month: "*", dayOfWeek: "1-5", 
            command: "/test/job.sh", rawLine: "test"
        )
        
        when: "checking against weekdays"
        def weekdayResults = (1..5).collect { day ->
            ScheduleAnalyzer.runsOnDayOfWeek(job, [day] as Set)
        }
        def weekendResults = [0, 6].collect { day ->
            ScheduleAnalyzer.runsOnDayOfWeek(job, [day] as Set)
        }
        
        then: "weekdays should match, weekends should not"
        weekdayResults.every { it == true }
        weekendResults.every { it == false }
    }
    
    def "runsInTimeRange should handle jobs with wildcard hour"() {
        given: "a job that runs every hour"
        def job = new CronJob(
            minute: "0", hour: "*", dayOfMonth: "*", 
            month: "*", dayOfWeek: "*", 
            command: "/test/job.sh", rawLine: "test"
        )
        def criteria = new QueryCriteria(
            queryType: QueryType.TIME_BASED,
            rawQuery: "between 9 AM and 5 PM",
            timeRangeStart: new Tuple2(9, 0),
            timeRangeEnd: new Tuple2(17, 0),
            isTimeBetween: true
        )
        
        when: "checking if job runs in time range"
        def result = ScheduleAnalyzer.runsInTimeRange(job, criteria)
        
        then: "it should match (runs during that time)"
        result == true
    }
}
