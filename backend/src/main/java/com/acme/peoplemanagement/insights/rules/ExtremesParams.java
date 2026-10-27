package com.acme.peoplemanagement.insights.rules;

/** What the extremes request says, as written. {@link ExtremesQuery#from} checks it and fills in the defaults. */
public record ExtremesParams(
        String order,
        String currency,
        Integer limit,
        String countryCode,
        Integer departmentId,
        Integer jobTitleId,
        String status) {
}
