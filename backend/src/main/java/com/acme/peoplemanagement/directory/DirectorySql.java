package com.acme.peoplemanagement.directory;

import com.acme.peoplemanagement.directory.rules.DirectoryQuery;
import com.acme.peoplemanagement.readmodel.CurrentSalarySql;
import com.acme.peoplemanagement.readmodel.EmployeeFilters;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the two statements of a directory request, a page and a count of the same rows. The text of a statement
 * comes only from fixed fragments. Every value from the request is a named parameter.
 */
final class DirectorySql {

    private static final String COLUMNS = "select e.id, e.full_name, e.email, d.name as department, "
            + "jt.name as job_title, c.name as country, e.hire_date, cs.amount_minor, cs.currency_code, "
            + "case when " + CurrentSalarySql.ACTIVE + " then 'ACTIVE' else 'FORMER' end as status";

    private DirectorySql() {
    }

    /** A page statement, the count statement for the same rows, and the parameters both use. */
    record Statements(String page, String count, Map<String, Object> parameters) {

        Statements {
            parameters = Map.copyOf(parameters);
        }
    }

    static Statements build(final DirectoryQuery query, final LocalDate asOf) {
        final Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("asOf", asOf);
        final String where = where(query, parameters);
        parameters.put("limit", query.size());
        parameters.put("offset", query.offset());
        return new Statements(
                COLUMNS + " " + CurrentSalarySql.EMPLOYEES + where + " order by " + query.sort().orderBy()
                        + " limit :limit offset :offset",
                "select count(*) " + CurrentSalarySql.EMPLOYEES + where,
                parameters);
    }

    private static String where(final DirectoryQuery query, final Map<String, Object> parameters) {
        final EmployeeFilters filters = new EmployeeFilters(query.countryCode(), query.departmentId(),
                query.jobTitleId(), query.currency(), query.status());
        final List<String> conditions = filters.conditions(parameters);
        if (query.searchPattern() != null) {
            conditions.add("(e.full_name ilike :search escape '\\' or e.email ilike :search escape '\\')");
            parameters.put("search", query.searchPattern());
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }
}
