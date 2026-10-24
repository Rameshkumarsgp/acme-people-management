package com.acme.peoplemanagement.directory;

import com.acme.peoplemanagement.directory.rules.DirectoryQuery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the two statements of a directory request, a page and a count of the same rows. The text of a statement
 * comes only from fixed fragments. Every value from the request is a named parameter.
 */
final class DirectorySql {

    /** Active means still employed on {@code asOf}. The row's status and the status filter both use this. */
    private static final String ACTIVE = "(e.termination_date is null or e.termination_date >= :asOf)";

    private static final String FROM = """
            from employee e
            join department d on d.id = e.department_id
            join job_title jt on jt.id = e.job_title_id
            join country c on c.code = e.country_code
            join lateral (select s.amount_minor, s.currency_code from salary_entry s
                          where s.employee_id = e.id and s.effective_date <= :asOf
                          order by s.effective_date desc limit 1) cs on true""";

    private static final String COLUMNS = "select e.id, e.full_name, e.email, d.name as department, "
            + "jt.name as job_title, c.name as country, e.hire_date, cs.amount_minor, cs.currency_code, "
            + "case when " + ACTIVE + " then 'ACTIVE' else 'FORMER' end as status";

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
                COLUMNS + " " + FROM + where + " order by " + query.sort().orderBy() + " limit :limit offset :offset",
                "select count(*) " + FROM + where,
                parameters);
    }

    private static String where(final DirectoryQuery query, final Map<String, Object> parameters) {
        final List<String> conditions = new ArrayList<>();
        switch (query.status()) {
            case ACTIVE -> conditions.add(ACTIVE);
            case FORMER -> conditions.add("not " + ACTIVE);
            default -> { }
        }
        if (query.searchPattern() != null) {
            conditions.add("(e.full_name ilike :search escape '\\' or e.email ilike :search escape '\\')");
            parameters.put("search", query.searchPattern());
        }
        addEquals(conditions, parameters, "e.country_code", "countryCode", query.countryCode());
        addEquals(conditions, parameters, "e.department_id", "departmentId", query.departmentId());
        addEquals(conditions, parameters, "e.job_title_id", "jobTitleId", query.jobTitleId());
        addEquals(conditions, parameters, "cs.currency_code", "currency", query.currency());
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
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
}
