package com.acme.peoplemanagement.insights;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.insights.rules.ExtremesParams;
import com.acme.peoplemanagement.insights.rules.ExtremesQuery;
import com.acme.peoplemanagement.readmodel.CurrentSalarySql;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExtremesSqlTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 19);

    private static ExtremesSql.Statement build(final String order, final Integer limit) {
        return ExtremesSql.build(ExtremesQuery.from(
                new ExtremesParams(order, "INR", limit, null, null, null, null)), AS_OF);
    }

    @Test
    void listsTheEmployeeAndTheCurrentSalaryFromTheSharedJoin() {
        final ExtremesSql.Statement sql = build(null, null);

        assertThat(sql.text()).startsWith("select e.id, e.full_name, d.name as department, jt.name as job_title, "
                + "c.name as country, cs.amount_minor, cs.effective_date " + CurrentSalarySql.EMPLOYEES);
    }

    @Test
    void ranksTheHighestFirstAndBreaksTiesByEmployeeId() {
        assertThat(build("HIGHEST", null).text()).endsWith("order by cs.amount_minor desc, e.id limit :limit");
    }

    @Test
    void ranksTheLowestFirstAndBreaksTiesByEmployeeId() {
        assertThat(build("LOWEST", null).text()).endsWith("order by cs.amount_minor asc, e.id limit :limit");
    }

    @Test
    void keepsToTheCurrencyAndTheActiveEmployees() {
        final ExtremesSql.Statement sql = build(null, 5);

        assertThat(sql.text()).contains(" where (e.termination_date is null or e.termination_date >= :asOf) "
                + "and cs.currency_code = :currency order by");
        assertThat(sql.parameters()).containsEntry("currency", "INR").containsEntry("asOf", AS_OF)
                .containsEntry("limit", 5);
    }

    @Test
    void keepsTextFromTheClientOutOfTheSql() {
        final ExtremesSql.Statement sql = ExtremesSql.build(ExtremesQuery.from(
                new ExtremesParams(null, "'; drop table employee; --", null, null, null, null, null)), AS_OF);

        assertThat(sql.text()).doesNotContain("drop table");
        assertThat(sql.parameters()).containsEntry("currency", "'; drop table employee; --");
    }
}
