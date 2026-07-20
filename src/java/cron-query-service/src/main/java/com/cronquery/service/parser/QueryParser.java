package com.cronquery.service.parser;

import com.cronquery.service.exception.InvalidQueryException;

/**
 * Parses natural language queries into structured {@link QueryCriteria}.
 */
public interface QueryParser {

    /**
     * Parse a natural language query into structured criteria.
     *
     * @param query natural language query string
     * @return parsed query criteria
     * @throws InvalidQueryException if the query cannot be understood
     */
    QueryCriteria parse(String query) throws InvalidQueryException;

    /**
     * Normalize a query by removing common prefixes and noise words.
     * Idempotent: {@code normalize(normalize(q)).equals(normalize(q))}.
     *
     * @param query raw query string
     * @return normalized query string
     */
    String normalize(String query);
}
