package com.acme.peoplemanagement.directory.rules;

import com.acme.peoplemanagement.directory.errors.InvalidDirectoryRequestException;
import java.util.Arrays;
import java.util.Locale;

/**
 * How the directory orders its rows. The client names a field from a fixed list, and each field maps to a fixed SQL
 * expression, so no client text ever reaches the SQL. Ties are always broken by id, so pages never overlap.
 */
public final class DirectorySort {

    private static final String FIELD_LIST = "fullName, department, jobTitle, country, hireDate, salary";

    private final SortField field;
    private final boolean descending;

    private DirectorySort(final SortField field, final boolean descending) {
        this.field = field;
        this.descending = descending;
    }

    /** The fields the directory can be sorted by, with the SQL expression each one stands for. */
    private enum SortField {
        FULL_NAME("fullName", "lower(e.full_name)"),
        DEPARTMENT("department", "lower(d.name)"),
        JOB_TITLE("jobTitle", "lower(jt.name)"),
        COUNTRY("country", "lower(c.name)"),
        HIRE_DATE("hireDate", "e.hire_date"),
        SALARY("salary", "cs.amount_minor");

        private final String apiName;
        private final String expression;

        SortField(final String apiName, final String expression) {
            this.apiName = apiName;
            this.expression = expression;
        }
    }

    public static DirectorySort parse(final String sort, final String currency) {
        if (sort == null || sort.isBlank()) {
            return new DirectorySort(SortField.FULL_NAME, false);
        }
        final String[] parts = sort.strip().split(",", -1);
        final SortField field = Arrays.stream(SortField.values())
                .filter(candidate -> candidate.apiName.equals(parts[0]))
                .findFirst()
                .orElseThrow(() -> new InvalidDirectoryRequestException("sort", "Sort by one of " + FIELD_LIST + "."));
        if (field == SortField.SALARY && (currency == null || currency.isBlank())) {
            throw new InvalidDirectoryRequestException("sort", "Sorting by salary needs a currency, because salaries "
                    + "in different currencies cannot be compared.");
        }
        return new DirectorySort(field, isDescending(parts));
    }

    /** The ORDER BY list, ending with the id so that equal values give the same order on every page. */
    public String orderBy() {
        return field.expression + (descending ? " desc" : " asc") + ", e.id";
    }

    private static boolean isDescending(final String[] parts) {
        if (parts.length == 1) {
            return false;
        }
        final String direction = parts[1].toLowerCase(Locale.ROOT);
        if (parts.length > 2 || !("asc".equals(direction) || "desc".equals(direction))) {
            throw new InvalidDirectoryRequestException("sort", "The direction must be asc or desc.");
        }
        return "desc".equals(direction);
    }
}
