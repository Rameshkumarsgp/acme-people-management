package com.acme.peoplemanagement.insights.api;

/**
 * The salary statistics of one group in one currency, in minor units. The total is a decimal string, because a sum of
 * many amounts can pass the largest integer a browser keeps exactly. The other measures stay within one amount's cap.
 */
public record InsightGroup(
        String key,
        String label,
        String currency,
        long headcount,
        String total,
        long min,
        long median,
        long mean,
        long max) {
}
