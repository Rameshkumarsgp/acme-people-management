package com.acme.peoplemanagement.readmodel;

import java.util.Optional;

/**
 * The definitions every read model shares, written once as SQL: who is active, what the current salary is, and the
 * employees with their names and current salary.
 * Both take the day as the named parameter {@code asOf}, and both read the employee as {@code e}.
 */
public final class CurrentSalarySql {

    /** Active means still employed on {@code asOf}, which includes the termination date itself. */
    public static final String ACTIVE = "(e.termination_date is null or e.termination_date >= :asOf)";

    /** Joins each employee's current salary as {@code cs}: the latest entry dated on or before {@code asOf}. */
    public static final String JOIN = """
            join lateral (select s.amount_minor, s.currency_code, s.effective_date from salary_entry s
                          where s.employee_id = e.id and s.effective_date <= :asOf
                          order by s.effective_date desc limit 1) cs on true""";

    /** Every employee with department {@code d}, title {@code jt}, country {@code c} and current salary {@code cs}. */
    public static final String EMPLOYEES = """
            from employee e
            join department d on d.id = e.department_id
            join job_title jt on jt.id = e.job_title_id
            join country c on c.code = e.country_code
            """ + JOIN;

    private CurrentSalarySql() {
    }

    /** The condition that keeps the employees of a status, or nothing when every employee is wanted. */
    public static Optional<String> statusCondition(final StatusFilter status) {
        return switch (status) {
            case ACTIVE -> Optional.of(ACTIVE);
            case FORMER -> Optional.of("not " + ACTIVE);
            case ALL -> Optional.empty();
        };
    }
}
