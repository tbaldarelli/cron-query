package com.cronquery.service.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

import com.cronquery.service.exception.CronParseException;
import com.cronutils.model.Cron;

public class CronParserImplTest {
    private CronParser cronParser;
    
    @BeforeEach
    void setUp() {
        cronParser = new CronParserImpl();
    }
    
    @ParameterizedTest(name = "{0} - {1}")
    @DisplayName("Should parse valid cron expressions")
    @CsvSource({
        "'0 8 * * *', 'Every day at 8 AM'",
        "'0 9-17 * * 1-5', 'Weekdays 9 AM to 5 PM'",
        "'0 8,12,18 * * *', 'Three times daily'",
        "'*/15 * * * *', 'Every 15 minutes'"
    })
    void testParseValidExpressions(String cronExpression, String description) throws CronParseException {
        Cron result = cronParser.parse(cronExpression);

        assertNotNull(result, "Parsed cron expression should not be null for: " + description);
        
        String formatted = cronParser.format(result);
        assertNotNull(formatted, "Formatted cron expression should not be null for: " + description);
        assertFalse(formatted.isEmpty(),
            "Formatted cron expression should not be empty for: " + description);

        /* Note: cron-utils might normalize things, so they might not match.  We decide later
         *  if that is important.  If it does normalize things and this breaks the unit test, maybe
         *  choose a different test
         */
        assertEquals(cronExpression, formatted, "Formatted cron should match input.");
    }
    
    // example of above tests by itself
    // @Test
    // @DisplayName("Should parse cron expression with steps")
    // void testParseWithSteps() {
    //     // Test: "*/15 * * * *" (every 15 minutes)
    //     // TODO: Implement test
    // }
    
    @Test
    @DisplayName("Should throw exception for invalid expression")
    void testParseInvalidExpression() {
        String invalidCronExpression = "invalid cron";
        CronParseException exception = assertThrows(CronParseException.class,
            () -> cronParser.parse(invalidCronExpression));
        assertEquals(invalidCronExpression, exception.getInvalidExpression());
    }
    
    @Test
    @DisplayName("Should validate returns true for valid expression")
    void testValidateValidExpression() {
        String validCronExpression = "0 8 * * *";
        assertTrue(cronParser.validate(validCronExpression), "Valid cron expression should return true.");
    }
    
    @Test
    @DisplayName("Should validate returns false for invalid expression")
    void testValidateInvalidExpression() {
        String invalidCronExpression = "invalid cron";
        assertFalse(cronParser.validate(invalidCronExpression), "Invalid cron expression should return false.");
    }
    
    @Test
    @DisplayName("Should format parsed cron back to string")
    void testFormat() throws CronParseException {
        String validCronExpression = "0 8 * * *";
        Cron cron = cronParser.parse(validCronExpression);
        assertNotNull(cron, "Parsed cron should not be null");

        assertInstanceOf(String.class, cronParser.format(cron), "Format should produce a string.");
    }
}
