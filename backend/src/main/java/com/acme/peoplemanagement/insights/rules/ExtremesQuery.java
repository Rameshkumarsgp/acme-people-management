package com.acme.peoplemanagement.insights.rules;

import com.acme.peoplemanagement.readmodel.EmployeeFilters;
import com.acme.peoplemanagement.readmodel.InvalidQueryException;

/**
 * A checked extremes request. The currency is always set, because amounts in different currencies cannot be ranked.
 * The limit is within its bounds.
 */
public record ExtremesQuery(ExtremesOrder order, int limit, EmployeeFilters filters) {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    public static ExtremesQuery from(final ExtremesParams params) {
        final EmployeeFilters filters = EmployeeFilters.from(params.countryCode(), params.departmentId(),
                params.jobTitleId(), params.currency(), params.status());
        if (filters.currency() == null) {
            throw new InvalidQueryException("currency", "Currency is required to rank salaries.");
        }
        return new ExtremesQuery(ExtremesOrder.parse(params.order()), limit(params.limit()), filters);
    }

    private static int limit(final Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidQueryException("limit", "Limit must be between 1 and " + MAX_LIMIT + ".");
        }
        return limit;
    }
}
