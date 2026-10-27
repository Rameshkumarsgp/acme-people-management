package com.acme.peoplemanagement.insights;

import com.acme.peoplemanagement.insights.rules.ExtremesQuery;
import com.acme.peoplemanagement.readmodel.CurrentSalarySql;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the statement of an extremes request: the highest or lowest paid employees of one currency. Ties are broken
 * by employee id so the list is stable. The text comes only from fixed fragments. Every value is a named parameter.
 */
final class ExtremesSql {

    private static final String COLUMNS = "select e.id, e.full_name, d.name as department, jt.name as job_title, "
            + "c.name as country, cs.amount_minor, cs.effective_date";

    private ExtremesSql() {
    }

    /** The statement and the parameters it uses. */
    record Statement(String text, Map<String, Object> parameters) {

        Statement {
            parameters = Map.copyOf(parameters);
        }
    }

    static Statement build(final ExtremesQuery query, final LocalDate asOf) {
        final Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("asOf", asOf);
        final List<String> conditions = query.filters().conditions(parameters);
        parameters.put("limit", query.limit());
        final String text = COLUMNS + " " + CurrentSalarySql.EMPLOYEES + " where " + String.join(" and ", conditions)
                + " order by cs.amount_minor " + query.order().direction() + ", e.id limit :limit";
        return new Statement(text, parameters);
    }
}
