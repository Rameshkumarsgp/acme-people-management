package com.acme.peoplemanagement.insights.rules;

/** What the insights request says, as written. {@link InsightsQuery#from} checks it and fills in the defaults. */
public record InsightsParams(
        String dimension,
        String countryCode,
        Integer departmentId,
        Integer jobTitleId,
        String currency,
        String status) {
}
