package com.acme.peoplemanagement.dataload.rules;

import java.util.Arrays;
import java.util.List;

/** The columns of the employee CSV, in the order the generator writes them. */
public enum Column {
    FULL_NAME("fullName", true),
    EMAIL("email", true),
    HIRE_DATE("hireDate", true),
    DEPARTMENT("department", true),
    JOB_TITLE("jobTitle", true),
    COUNTRY_CODE("countryCode", true),
    CURRENCY("currency", true),
    SALARY("salary", true),
    SALARY_EFFECTIVE_DATE("salaryEffectiveDate", false),
    TERMINATION_DATE("terminationDate", false);

    private final String header;
    private final boolean required;

    Column(final String header, final boolean required) {
        this.header = header;
        this.required = required;
    }

    public String header() {
        return header;
    }

    public boolean required() {
        return required;
    }

    public static List<String> headers() {
        return Arrays.stream(values()).map(Column::header).toList();
    }
}
