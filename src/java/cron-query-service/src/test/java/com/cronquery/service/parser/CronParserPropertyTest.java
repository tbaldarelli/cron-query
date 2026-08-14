package com.cronquery.service.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cronquery.CronParseException;
import com.cronutils.model.Cron;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

public class CronParserPropertyTest {

    @Provide
    Arbitrary<String> validCronExpressions() {
        Arbitrary<String> minutes = Arbitraries.integers().between(0, 59).map(String::valueOf);
        Arbitrary<String> hours = Arbitraries.integers().between(0, 23).map(String::valueOf);
        Arbitrary<String> doms = Arbitraries.integers().between(1, 31).map(String::valueOf);
        Arbitrary<String> months = Arbitraries.integers().between(1, 12).map(String::valueOf);
        Arbitrary<String> dows = Arbitraries.integers().between(0, 6).map(String::valueOf);

        return Combinators.combine(minutes, hours, doms, months, dows)
            .as((m, h, d, mo, dw) -> m + " " + h + " " + d + " " + mo + " " + dw);
    }

    /**
     * Feature: java-native-implementation, Property 1: Cron Expression Round-Trip
     * 
     * For any valid cron expression, parsing then formatting then parsing again
     * SHALL produce an equivalent cron expression.
     */
    @Property(tries = 100)
    @Tag("property-test")
    @Tag("cron-parser")
    void cronExpressionRoundTrip(@ForAll("validCronExpressions") String cronExpr) throws CronParseException {
        CronParserImpl cronParser = new CronParserImpl();
        Cron parsed1 = cronParser.parse(cronExpr);
        String formatted = cronParser.format(parsed1);
        Cron parsed2 = cronParser.parse(formatted);
        
        assertEquals(cronParser.format(parsed1), cronParser.format(parsed2));
    }

    @Provide
    Arbitrary<String> validInvalidCronExpressions() {
        Arbitrary<String> minutes = Arbitraries.integers().between(-1, 62).map(String::valueOf);
        Arbitrary<String> hours = Arbitraries.integers().between(0, 24).map(String::valueOf);
        Arbitrary<String> doms = Arbitraries.integers().between(1, 32).map(String::valueOf);
        Arbitrary<String> months = Arbitraries.integers().between(0, 12).map(String::valueOf);
        Arbitrary<String> dows = Arbitraries.integers().between(0, 6).map(String::valueOf);

        Arbitrary<String> structured = Combinators.combine(minutes, hours, doms, months, dows)
            .as((m, h, d, mo, dw) -> m + " " + h + " " + d + " " + mo + " " + dw);

        Arbitrary<String> garbage = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);

        return Arbitraries.oneOf(structured, garbage);
    }

    /**
     * Feature: java-native-implementation, Property 2: Cron Validation Consistency
     * 
     * For any cron expression (valid or invalid), confirm that if it is valid, it can be parsed, and vice versa.
     * SHALL produce an equivalent cron expression.
     */
    @Property(tries = 100)
    @Tag("property-test")
    @Tag("cron-parser")
    void cronValidationConsistency(@ForAll("validInvalidCronExpressions") String cronExpr) {
        CronParserImpl cronParser = new CronParserImpl();
        boolean valid = cronParser.validate(cronExpr);

        boolean validParse = false;
        try {
            cronParser.parse(cronExpr);
            validParse = true;
        } catch (com.cronquery.service.exception.CronParseException e) {
            validParse = false;
        }
        
        assertEquals(valid, validParse);
    }
}
