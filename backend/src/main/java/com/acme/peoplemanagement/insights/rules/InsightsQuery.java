package com.acme.peoplemanagement.insights.rules;

import com.acme.peoplemanagement.readmodel.EmployeeFilters;

/** A checked insights request: the dimension to slice by and the employees to include. */
public record InsightsQuery(Dimension dimension, EmployeeFilters filters) {

    public static InsightsQuery from(final InsightsParams params) {
        return new InsightsQuery(
                Dimension.parse(params.dimension()),
                EmployeeFilters.from(params.countryCode(), params.departmentId(), params.jobTitleId(),
                        params.currency(), params.status()));
    }
}
