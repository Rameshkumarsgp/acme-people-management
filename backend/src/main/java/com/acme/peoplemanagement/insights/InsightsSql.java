package com.acme.peoplemanagement.insights;

import com.acme.peoplemanagement.insights.rules.InsightsQuery;
import com.acme.peoplemanagement.readmodel.CurrentSalarySql;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the statement of an insights request: the six measures of each group, per currency. The text comes only
 * from fixed fragments. Every value from the request is a named parameter.
 */
final class InsightsSql {

    private static final String MEASURES = "count(*) as headcount, "
            + "sum(cs.amount_minor::numeric) as total, "
            + "min(cs.amount_minor) as min_amount, "
            + "round(percentile_cont(0.5) within group (order by cs.amount_minor)::numeric) as median_amount, "
            + "round(avg(cs.amount_minor::numeric)) as mean_amount, "
            + "max(cs.amount_minor) as max_amount";

    private InsightsSql() {
    }

    /** The statement and the parameters it uses. */
    record Statement(String text, Map<String, Object> parameters) {

        Statement {
            parameters = Map.copyOf(parameters);
        }
    }

    static Statement build(final InsightsQuery query, final LocalDate asOf) {
        final Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("asOf", asOf);
        final List<String> conditions = query.filters().conditions(parameters);
        final String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        final String text = "select " + query.dimension().keySql() + " as group_key, "
                + query.dimension().labelSql() + " as group_label, cs.currency_code as currency, " + MEASURES + " "
                + CurrentSalarySql.EMPLOYEES + where
                + " group by 1, 2, 3 order by " + query.dimension().orderBy() + ", currency";
        return new Statement(text, parameters);
    }
}
