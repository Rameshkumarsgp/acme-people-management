package com.acme.peoplemanagement.readmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The filters the directory and the insights share, turned into SQL conditions. The text of a condition is fixed.
 * Every value is a named parameter.
 */
public record EmployeeFilters(
        String countryCode, Integer departmentId, Integer jobTitleId, String currency, StatusFilter status) {

    public static EmployeeFilters from(
            final String countryCode,
            final Integer departmentId,
            final Integer jobTitleId,
            final String currency,
            final String status) {
        return new EmployeeFilters(
                blankToNull(countryCode), departmentId, jobTitleId, blankToNull(currency), StatusFilter.parse(status));
    }

    /** The conditions to join with {@code and}. Adds the values they use to {@code parameters}. */
    public List<String> conditions(final Map<String, Object> parameters) {
        final List<String> conditions = new ArrayList<>();
        CurrentSalarySql.statusCondition(status).ifPresent(conditions::add);
        addEquals(conditions, parameters, "e.country_code", "countryCode", countryCode);
        addEquals(conditions, parameters, "e.department_id", "departmentId", departmentId);
        addEquals(conditions, parameters, "e.job_title_id", "jobTitleId", jobTitleId);
        addEquals(conditions, parameters, "cs.currency_code", "currency", currency);
        return conditions;
    }

    private static void addEquals(
            final List<String> conditions,
            final Map<String, Object> parameters,
            final String column,
            final String name,
            final Object value) {
        if (value != null) {
            conditions.add(column + " = :" + name);
            parameters.put(name, value);
        }
    }

    private static String blankToNull(final String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
