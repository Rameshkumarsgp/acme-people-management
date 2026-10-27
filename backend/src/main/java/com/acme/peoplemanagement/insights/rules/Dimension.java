package com.acme.peoplemanagement.insights.rules;

import com.acme.peoplemanagement.readmodel.InvalidQueryException;
import java.util.Arrays;

/**
 * What the insights slice salary by. Each dimension owns the fixed SQL for its group key, its label and its order,
 * so no text from a request ever reaches a statement. They read the aliases of the insights query: {@code e},
 * {@code d}, {@code jt} and {@code c}, and the parameter {@code asOf}.
 */
public enum Dimension {
    OVERALL("'ALL'", "'All employees'", "group_label"),
    COUNTRY("e.country_code", "c.name", "group_label"),
    DEPARTMENT("e.department_id::text", "d.name", "group_label"),
    JOB_TITLE("e.job_title_id::text", "jt.name", "group_label"),
    TITLE_COUNTRY("e.job_title_id || '-' || e.country_code", "jt.name || ' in ' || c.name", "group_label"),
    TENURE(TenureBands.KEY, TenureBands.LABEL, "group_key");

    private final String keySql;
    private final String labelSql;
    private final String orderBy;

    Dimension(final String keySql, final String labelSql, final String orderBy) {
        this.keySql = keySql;
        this.labelSql = labelSql;
        this.orderBy = orderBy;
    }

    public static Dimension parse(final String text) {
        if (text == null || text.isBlank()) {
            return OVERALL;
        }
        return Arrays.stream(values())
                .filter(dimension -> dimension.name().equalsIgnoreCase(text.strip()))
                .findFirst()
                .orElseThrow(() -> new InvalidQueryException("dimension",
                        "Dimension must be OVERALL, COUNTRY, DEPARTMENT, JOB_TITLE, TITLE_COUNTRY or TENURE."));
    }

    public String keySql() {
        return keySql;
    }

    public String labelSql() {
        return labelSql;
    }

    /** The output column to order the groups by: the label, or the key where the key is the natural order. */
    public String orderBy() {
        return orderBy;
    }

    /** Tenure in completed years, from the hire date to the earlier of today and the termination date. */
    private static final class TenureBands {

        private static final String YEARS =
                "extract(year from age(least(:asOf, coalesce(e.termination_date, :asOf)), e.hire_date))";
        private static final String KEY = "case when " + YEARS + " < 1 then '0' when " + YEARS + " < 3 then '1' when "
                + YEARS + " < 5 then '2' when " + YEARS + " < 10 then '3' else '4' end";
        private static final String LABEL = "case " + KEY + " when '0' then 'Under 1 year' "
                + "when '1' then '1 to under 3 years' when '2' then '3 to under 5 years' "
                + "when '3' then '5 to under 10 years' else '10 or more years' end";

        private TenureBands() {
        }
    }
}
