package com.cronquery

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDateTime

/**
 * Spock tests for QueryParser module.
 * 
 * Tests cover: day queries, time queries, combined queries, query normalization
 */
class QueryParserSpec extends Specification {
    
    def "parseQuery should reject empty queries"() {
        when: "parsing an empty query"
        QueryParser.parseQuery(query)
        
        then: "it should throw QueryParseException"
        def exception = thrown(QueryParseException)
        exception.message.contains("Empty query")
        
        where:
        query << ["", "   ", "\t"]
    }
    
    @Unroll
    def "parseQuery should parse day name: #query as day #expectedDays"() {
        when: "parsing a day query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should parse correctly"
        criteria.queryType == QueryType.DAY_BASED
        criteria.daysOfWeek == expectedDays as Set
        criteria.isSpecificDate == false
        
        where:
        query       | expectedDays
        "Saturday"  | [6]
        "sunday"    | [0]
        "MONDAY"    | [1]
        "tue"       | [2]
        "Wed"       | [3]
        "thursday"  | [4]
        "Fri"       | [5]
    }
    
    def "parseQuery should parse weekdays"() {
        given: "a weekdays query"
        def queries = ["weekdays", "weekday", "which jobs run on weekdays"]
        
        when: "parsing each query"
        def results = queries.collect { QueryParser.parseQuery(it) }
        
        then: "all should match weekdays"
        results.every { it.queryType == QueryType.DAY_BASED }
        results.every { it.daysOfWeek == [1, 2, 3, 4, 5] as Set }
        results.every { it.weekdaysOnly == true }
    }
    
    def "parseQuery should parse weekends"() {
        given: "a weekends query"
        def queries = ["weekends", "weekend", "which jobs run on weekends"]
        
        when: "parsing each query"
        def results = queries.collect { QueryParser.parseQuery(it) }
        
        then: "all should match weekends"
        results.every { it.queryType == QueryType.DAY_BASED }
        results.every { it.daysOfWeek == [0, 6] as Set }
        results.every { it.weekendsOnly == true }
    }
    
    @Unroll
    def "parseQuery should parse 12-hour time: #query as #expectedHour:#expectedMinute"() {
        when: "parsing a 12-hour time query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should parse correctly"
        criteria.queryType == QueryType.TIME_BASED
        criteria.timeHour == expectedHour
        criteria.timeMinute == expectedMinute
        
        where:
        query       | expectedHour | expectedMinute
        "8 AM"      | 8            | 0
        "8:30 AM"   | 8            | 30
        "3 PM"      | 15           | 0
        "3:45 PM"   | 15           | 45
        "12 AM"     | 0            | 0
        "12 PM"     | 12           | 0
        "11:59 PM"  | 23           | 59
    }
    
    @Unroll
    def "parseQuery should parse 24-hour time: #query as #expectedHour:#expectedMinute"() {
        when: "parsing a 24-hour time query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should parse correctly"
        criteria.queryType == QueryType.TIME_BASED
        criteria.timeHour == expectedHour
        criteria.timeMinute == expectedMinute
        
        where:
        query    | expectedHour | expectedMinute
        "14:00"  | 14           | 0
        "9:30"   | 9            | 30
        "0:00"   | 0            | 0
        "23:59"  | 23           | 59
    }
    
    def "parseQuery should parse 'after' time queries"() {
        given: "an 'after' time query"
        def query = "after 10 AM"
        
        when: "parsing the query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should set time range correctly"
        criteria.queryType == QueryType.TIME_BASED
        criteria.isTimeAfter == true
        criteria.timeRangeStart.first == 10
        criteria.timeRangeStart.second == 0
    }
    
    def "parseQuery should parse 'before' time queries"() {
        given: "a 'before' time query"
        def query = "before 5 PM"
        
        when: "parsing the query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should set time range correctly"
        criteria.queryType == QueryType.TIME_BASED
        criteria.isTimeBefore == true
        criteria.timeRangeEnd.first == 17
        criteria.timeRangeEnd.second == 0
    }
    
    def "parseQuery should parse 'between' time queries"() {
        given: "a 'between' time query"
        def query = "between 9 AM and 5 PM"
        
        when: "parsing the query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should set time range correctly"
        criteria.queryType == QueryType.TIME_BASED
        criteria.isTimeBetween == true
        criteria.timeRangeStart.first == 9
        criteria.timeRangeStart.second == 0
        criteria.timeRangeEnd.first == 17
        criteria.timeRangeEnd.second == 0
    }
    
    def "parseQuery should parse combined day and time queries"() {
        given: "a combined day and time query"
        def query = "saturday after 10 AM"
        
        when: "parsing the query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should parse both components"
        criteria.queryType == QueryType.COMBINED
        criteria.daysOfWeek == [6] as Set
        criteria.isTimeAfter == true
        criteria.timeRangeStart.first == 10
    }
    
    def "parseQuery should parse 'this monday' queries"() {
        given: "a 'this monday' query"
        def query = "this monday"
        
        when: "parsing the query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should mark as specific date"
        criteria.queryType == QueryType.DAY_BASED
        criteria.daysOfWeek.contains(1)
        criteria.isSpecificDate == true
    }
    
    def "parseQuery should parse 'today' queries"() {
        given: "a 'today' query"
        def query = "today"
        
        when: "parsing the query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should use current day"
        criteria.queryType == QueryType.DAY_BASED
        criteria.isSpecificDate == true
        criteria.specificDate != null
        
        and: "the day should match today"
        def today = LocalDateTime.now().dayOfWeek.value % 7
        criteria.daysOfWeek.contains(today)
    }
    
    def "parseQuery should parse 'tomorrow' queries"() {
        given: "a 'tomorrow' query"
        def query = "tomorrow"
        
        when: "parsing the query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should use tomorrow's day"
        criteria.queryType == QueryType.DAY_BASED
        criteria.isSpecificDate == true
        criteria.specificDate != null
        
        and: "the day should match tomorrow"
        def tomorrow = LocalDateTime.now().plusDays(1).dayOfWeek.value % 7
        criteria.daysOfWeek.contains(tomorrow)
    }
    
    def "parseQuery should return UNKNOWN for unparseable queries"() {
        given: "a query that doesn't match any pattern"
        def query = "some random text that makes no sense"
        
        when: "parsing the query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should return UNKNOWN type"
        criteria.queryType == QueryType.UNKNOWN
        criteria.rawQuery == query.toLowerCase()
    }
    
    def "formatCriteriaDescription should format day-based criteria"() {
        given: "a day-based criteria"
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "saturday",
            daysOfWeek: [6] as Set
        )
        
        when: "formatting the description"
        def description = QueryParser.formatCriteriaDescription(criteria)
        
        then: "it should describe the day"
        description.toLowerCase().contains("saturday")
    }
    
    def "formatCriteriaDescription should format weekdays"() {
        given: "a weekdays criteria"
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "weekdays",
            daysOfWeek: [1, 2, 3, 4, 5] as Set,
            weekdaysOnly: true
        )
        
        when: "formatting the description"
        def description = QueryParser.formatCriteriaDescription(criteria)
        
        then: "it should say 'weekdays'"
        description == "weekdays"
    }
    
    def "formatCriteriaDescription should format weekends"() {
        given: "a weekends criteria"
        def criteria = new QueryCriteria(
            queryType: QueryType.DAY_BASED,
            rawQuery: "weekends",
            daysOfWeek: [0, 6] as Set,
            weekendsOnly: true
        )
        
        when: "formatting the description"
        def description = QueryParser.formatCriteriaDescription(criteria)
        
        then: "it should say 'weekends'"
        description == "weekends"
    }
    
    def "formatCriteriaDescription should format time-based criteria"() {
        given: "a time-based criteria"
        def criteria = new QueryCriteria(
            queryType: QueryType.TIME_BASED,
            rawQuery: "8 AM",
            timeHour: 8,
            timeMinute: 0
        )
        
        when: "formatting the description"
        def description = QueryParser.formatCriteriaDescription(criteria)
        
        then: "it should show the time"
        description.contains("8 AM")
    }
    
    def "formatCriteriaDescription should format 'after' time criteria"() {
        given: "an 'after' time criteria"
        def criteria = new QueryCriteria(
            queryType: QueryType.TIME_BASED,
            rawQuery: "after 10 AM",
            timeRangeStart: new Tuple2(10, 0),
            isTimeAfter: true
        )
        
        when: "formatting the description"
        def description = QueryParser.formatCriteriaDescription(criteria)
        
        then: "it should show 'after 10 AM'"
        description.contains("after")
        description.contains("10 AM")
    }
    
    def "formatCriteriaDescription should format 'between' time criteria"() {
        given: "a 'between' time criteria"
        def criteria = new QueryCriteria(
            queryType: QueryType.TIME_BASED,
            rawQuery: "between 9 AM and 5 PM",
            timeRangeStart: new Tuple2(9, 0),
            timeRangeEnd: new Tuple2(17, 0),
            isTimeBetween: true
        )
        
        when: "formatting the description"
        def description = QueryParser.formatCriteriaDescription(criteria)
        
        then: "it should show the range"
        description.contains("between")
        description.contains("9 AM")
        description.contains("5 PM")
    }
    
    def "formatCriteriaDescription should format combined criteria"() {
        given: "a combined criteria"
        def criteria = new QueryCriteria(
            queryType: QueryType.COMBINED,
            rawQuery: "saturday after 10 AM",
            daysOfWeek: [6] as Set,
            timeRangeStart: new Tuple2(10, 0),
            isTimeAfter: true
        )
        
        when: "formatting the description"
        def description = QueryParser.formatCriteriaDescription(criteria)
        
        then: "it should show both day and time"
        description.toLowerCase().contains("saturday")
        description.contains("after")
        description.contains("10 AM")
    }
    
    def "DAY_NAMES map should contain all standard day names"() {
        expect: "all day names and abbreviations to be defined"
        QueryParser.DAY_NAMES.containsKey("sunday")
        QueryParser.DAY_NAMES.containsKey("sun")
        QueryParser.DAY_NAMES.containsKey("monday")
        QueryParser.DAY_NAMES.containsKey("mon")
        QueryParser.DAY_NAMES.containsKey("tuesday")
        QueryParser.DAY_NAMES.containsKey("tue")
        QueryParser.DAY_NAMES.containsKey("wednesday")
        QueryParser.DAY_NAMES.containsKey("wed")
        QueryParser.DAY_NAMES.containsKey("thursday")
        QueryParser.DAY_NAMES.containsKey("thu")
        QueryParser.DAY_NAMES.containsKey("friday")
        QueryParser.DAY_NAMES.containsKey("fri")
        QueryParser.DAY_NAMES.containsKey("saturday")
        QueryParser.DAY_NAMES.containsKey("sat")
    }
    
    def "DAY_NAMES should map to correct day numbers (0=Sunday)"() {
        expect: "correct day number mappings"
        QueryParser.DAY_NAMES["sunday"] == 0
        QueryParser.DAY_NAMES["monday"] == 1
        QueryParser.DAY_NAMES["tuesday"] == 2
        QueryParser.DAY_NAMES["wednesday"] == 3
        QueryParser.DAY_NAMES["thursday"] == 4
        QueryParser.DAY_NAMES["friday"] == 5
        QueryParser.DAY_NAMES["saturday"] == 6
    }
    
    def "WEEKDAYS constant should contain Monday-Friday"() {
        expect: "weekdays set to contain correct days"
        QueryParser.WEEKDAYS == [1, 2, 3, 4, 5] as Set
    }
    
    def "WEEKENDS constant should contain Saturday and Sunday"() {
        expect: "weekends set to contain correct days"
        QueryParser.WEEKENDS == [0, 6] as Set
    }
    
    def "parseQuery should handle case-insensitive input"() {
        given: "queries with different cases"
        def queries = ["SATURDAY", "Saturday", "saturday", "SaTuRdAy"]
        
        when: "parsing each query"
        def results = queries.collect { QueryParser.parseQuery(it) }
        
        then: "all should parse to the same result"
        results.every { it.queryType == QueryType.DAY_BASED }
        results.every { it.daysOfWeek == [6] as Set }
    }
    
    def "parseQuery should normalize whitespace"() {
        given: "a query with extra whitespace"
        def query = "  saturday   after   10  AM  "
        
        when: "parsing the query"
        def criteria = QueryParser.parseQuery(query)
        
        then: "it should parse correctly despite whitespace"
        criteria.queryType == QueryType.COMBINED
        criteria.daysOfWeek == [6] as Set
        criteria.isTimeAfter == true
    }
}
