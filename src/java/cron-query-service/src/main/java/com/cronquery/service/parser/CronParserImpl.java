package com.cronquery.service.parser;

import org.springframework.stereotype.Component;

import com.cronquery.service.exception.CronParseException;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;

@Component
public class CronParserImpl implements CronParser {

    private com.cronutils.parser.CronParser parser;

    public CronParserImpl() {
        // Configure for Unix 5-field cron format
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
        this.parser = new com.cronutils.parser.CronParser(cronDefinition);
    }

    @Override
    public Cron parse(String cronExpression) throws CronParseException {
        if (cronExpression == null || cronExpression.isEmpty()) {
            throw new CronParseException("Cron expression cannot be null or empty.", cronExpression);
        }

        try {
            return parser.parse(cronExpression);            
        } catch (Exception e) {
            throw new CronParseException("Failed to parse cron expression.", cronExpression, e);
        }
    }

    @Override
    public boolean validate(String cronExpression) {
        try {
            parser.parse(cronExpression);
            return true;
        } catch (Exception e) { 
            return false;
        }
    }

    @Override
    public String format(Cron cron) {
        if (cron == null) return null;

        return cron.asString();
    }

}
