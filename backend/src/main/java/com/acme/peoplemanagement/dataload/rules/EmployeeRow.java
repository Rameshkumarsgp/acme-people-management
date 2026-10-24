package com.acme.peoplemanagement.dataload.rules;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;

/** One employee as the CSV describes them, with the names and the amount still as written. */
public record EmployeeRow(
        String fullName,
        String email,
        LocalDate hireDate,
        String department,
        String jobTitle,
        String countryCode,
        String currency,
        String salary,
        LocalDate salaryEffectiveDate,
        LocalDate terminationDate) {

    private static final String DATE_HINT = "Dates must look like 2024-04-01 (year-month-day).";

    public static EmployeeRow parse(final Map<Column, String> cells) {
        return new EmployeeRow(
                required(cells, Column.FULL_NAME),
                required(cells, Column.EMAIL),
                date(required(cells, Column.HIRE_DATE), Column.HIRE_DATE),
                required(cells, Column.DEPARTMENT),
                required(cells, Column.JOB_TITLE),
                required(cells, Column.COUNTRY_CODE),
                required(cells, Column.CURRENCY),
                required(cells, Column.SALARY),
                optionalDate(cells, Column.SALARY_EFFECTIVE_DATE),
                optionalDate(cells, Column.TERMINATION_DATE));
    }

    public Map<Column, String> cells() {
        final Map<Column, String> cells = new EnumMap<>(Column.class);
        cells.put(Column.FULL_NAME, fullName);
        cells.put(Column.EMAIL, email);
        cells.put(Column.HIRE_DATE, hireDate.toString());
        cells.put(Column.DEPARTMENT, department);
        cells.put(Column.JOB_TITLE, jobTitle);
        cells.put(Column.COUNTRY_CODE, countryCode);
        cells.put(Column.CURRENCY, currency);
        cells.put(Column.SALARY, salary);
        cells.put(Column.SALARY_EFFECTIVE_DATE, salaryEffectiveDate == null ? "" : salaryEffectiveDate.toString());
        cells.put(Column.TERMINATION_DATE, terminationDate == null ? "" : terminationDate.toString());
        return cells;
    }

    private static String cell(final Map<Column, String> cells, final Column column) {
        return cells.getOrDefault(column, "");
    }

    private static String required(final Map<Column, String> cells, final Column column) {
        final String value = cell(cells, column);
        if (value.isEmpty()) {
            throw new RowRejectedException(column.header(), "A value is required.");
        }
        return value;
    }

    private static LocalDate optionalDate(final Map<Column, String> cells, final Column column) {
        final String value = cell(cells, column);
        return value.isEmpty() ? null : date(value, column);
    }

    private static LocalDate date(final String value, final Column column) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new RowRejectedException(column.header(), DATE_HINT);
        }
    }
}
